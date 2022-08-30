/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.graphql.dgs.webflux.handlers

import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import com.netflix.graphql.types.subscription.websockets.CloseCode
import com.netflix.graphql.types.subscription.websockets.Message
import graphql.ExecutionResult
import graphql.GraphqlErrorBuilder
import org.reactivestreams.Publisher
import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import org.springframework.core.ResolvableType
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.util.MimeTypeUtils
import org.springframework.web.reactive.socket.CloseStatus
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class WebsocketGraphQLTransportWSProtocolHandler(private val dgsReactiveQueryExecutor: DgsReactiveQueryExecutor, private val connectionInitTimeout: Duration) : WebsocketReactiveProtocolHandler {

    private val resolvableType = ResolvableType.forType(Message::class.java)
    private val sessions = ConcurrentHashMap<String, MutableMap<String, Subscription>>()
    private val connections = ConcurrentHashMap<String, Boolean>()
    private val decoder = Jackson2JsonDecoder()
    private val encoder = Jackson2JsonEncoder(decoder.objectMapper)

    override fun handle(webSocketSession: WebSocketSession): Mono<Void> {

        connections[webSocketSession.id] = false

        Mono.delay(connectionInitTimeout).then(
            Mono.defer {
                if (connections[webSocketSession.id] == false) {
                    webSocketSession.close(CloseStatus(CloseCode.ConnectionInitialisationTimeout.code, "Did not receive a ConnectionInitMessage"))
                } else Mono.empty()
            }
        )
            .subscribe()

        return webSocketSession.send(
            webSocketSession.receive()
                .flatMap outer@{ message ->
                    val buffer: DataBuffer = DataBufferUtils.retain(message.payload)
                    val operationMessage: Message = decoder.decode(
                        buffer,
                        resolvableType,
                        MimeTypeUtils.APPLICATION_JSON,
                        null
                    ) as Message

                    when (operationMessage) {
                        is Message.ConnectionInitMessage -> {
                            if (connections[webSocketSession.id]!!) {
                                // we've already received a connection request and this must be an error
                                return@outer webSocketSession.close(CloseStatus(CloseCode.TooManyInitialisationRequests.code, "Too many connection initialisation requests")).thenMany(Mono.empty())
                            }
                            connections[webSocketSession.id] = true
                            Flux.just(
                                toWebsocketMessage(
                                    Message.ConnectionAckMessage(), webSocketSession
                                )
                            )
                        }
                        is Message.SubscribeMessage -> {
                            val queryPayload = decoder.objectMapper.convertValue<Message.SubscribeMessage.Payload>(
                                operationMessage.payload
                            )
                            if (sessions.containsKey(webSocketSession.id)) {
                                return@outer webSocketSession.close(CloseStatus(CloseCode.SubscriberAlreadyExists.code, "Subscriber for ${webSocketSession.id} already exists")).thenMany(Mono.empty())
                            }
                            logger.debug("Starting subscription {} for session {}", queryPayload, webSocketSession.id)
                            dgsReactiveQueryExecutor.execute(queryPayload.query, queryPayload.variables)
                                .flatMapMany { executionResult ->
                                    val publisher: Publisher<ExecutionResult> = executionResult.getData()
                                    Flux.from(publisher).map { er ->
                                        toWebsocketMessage(
                                            Message.NextMessage(
                                                payload = com.netflix.graphql.types.subscription.websockets.ExecutionResult(er.getData(), er.errors),
                                                id = operationMessage.id
                                            ),
                                            webSocketSession
                                        )
                                    }.doOnSubscribe {
                                        if (operationMessage.id != null) {
                                            sessions[webSocketSession.id] = mutableMapOf(operationMessage.id to it)
                                        }
                                    }.doOnComplete {
                                        webSocketSession.send(
                                            Flux.just(
                                                toWebsocketMessage(
                                                    Message.CompleteMessage(operationMessage.id),
                                                    webSocketSession
                                                )
                                            )
                                        ).subscribe()

                                        sessions[webSocketSession.id]?.remove(operationMessage.id)
                                        logger.debug(
                                            "Completing subscription {} for connection {}",
                                            operationMessage.id, webSocketSession.id
                                        )
                                    }.doOnError {
                                        webSocketSession.send(
                                            Flux.just(
                                                toWebsocketMessage(
                                                    Message.ErrorMessage(payload = listOf(GraphqlErrorBuilder.newError().message(it.message).build()), id = operationMessage.id),
                                                    webSocketSession
                                                )
                                            )
                                        ).subscribe()

                                        sessions[webSocketSession.id]?.remove(operationMessage.id)
                                        logger.debug(
                                            "Subscription publisher error for input {} for subscription {} for connection {}",
                                            queryPayload, operationMessage.id, webSocketSession.id, it
                                        )
                                    }
                                }
                        }
                        is Message.PingMessage ->
                            Flux.just(
                                toWebsocketMessage(
                                    Message.PongMessage(),
                                    webSocketSession
                                )
                            )
                        is Message.PongMessage -> Flux.empty()
                        is Message.CompleteMessage -> {
                            sessions[webSocketSession.id]?.remove(operationMessage.id)?.cancel()
                            logger.debug(
                                "Client stopped subscription {} for connection {}",
                                operationMessage.id, webSocketSession.id
                            )
                            Flux.empty()
                        }
                        else -> {
                            return@outer webSocketSession.close(CloseStatus(CloseCode.BadRequest.code, "Unrecognized message")).thenMany(Mono.empty())
                        }
                    }
                }
                .log()
                .doFinally {
                    logger.debug("Cleaning up subscriptions for session ${webSocketSession.id}")
                    sessions[webSocketSession.id]?.forEach {
                        it.value.cancel()
                    }
                    sessions.remove(webSocketSession.id)
                    connections.remove(webSocketSession.id)
                }
        )
    }

    private fun toWebsocketMessage(operationMessage: Message, session: WebSocketSession): WebSocketMessage {
        return WebSocketMessage(
            WebSocketMessage.Type.TEXT,
            encoder.encodeValue(
                operationMessage,
                session.bufferFactory(),
                resolvableType,
                MimeTypeUtils.APPLICATION_JSON,
                null
            )
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DgsReactiveQueryExecutor::class.java)
    }
}
