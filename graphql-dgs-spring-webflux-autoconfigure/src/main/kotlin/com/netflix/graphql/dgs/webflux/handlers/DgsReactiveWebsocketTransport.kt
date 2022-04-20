/*
 * Copyright 2022 Netflix, Inc.
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

import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import com.netflix.graphql.dgs.transports.websockets.GRAPHQL_TRANSPORT_WS_PROTOCOL
import com.netflix.graphql.dgs.transports.websockets.GraphQLWebsocketMessage
import com.netflix.graphql.dgs.transports.websockets.WebSocketInterceptor
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
import org.springframework.util.CollectionUtils
import org.springframework.util.MimeTypeUtils
import org.springframework.web.reactive.socket.CloseStatus
import org.springframework.web.reactive.socket.HandshakeInfo
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * WebSocketHandler for GraphQL based on
 * <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL Over WebSocket Protocol</a> and
 * for use in DGS framework.
 */
class DgsReactiveWebsocketTransport(
    private val dgsReactiveQueryExecutor: DgsReactiveQueryExecutor,
    private val webSocketInterceptor: WebSocketInterceptor? = null,
) : WebSocketHandler {

    private val resolvableType = ResolvableType.forType(GraphQLWebsocketMessage::class.java)

    private val decoder = Jackson2JsonDecoder()
    private val encoder = Jackson2JsonEncoder(decoder.objectMapper)

    override fun getSubProtocols(): List<String> = listOf(GRAPHQL_TRANSPORT_WS_PROTOCOL)

    override fun handle(webSocketSession: WebSocketSession): Mono<Void> {

        val handshakeInfo: HandshakeInfo = webSocketSession.handshakeInfo

        // Session state
        val connectionInitPayloadRef = AtomicReference<Map<String, Any>>()
        val subscriptions: MutableMap<String, Subscription> = ConcurrentHashMap()

        return webSocketSession.send(
            webSocketSession.receive()
                .flatMap { webSocketMessage ->
                    val buffer: DataBuffer = DataBufferUtils.retain(webSocketMessage.payload)

                    val message: GraphQLWebsocketMessage = decoder.decode(
                        buffer,
                        resolvableType,
                        MimeTypeUtils.APPLICATION_JSON,
                        null
                    ) as GraphQLWebsocketMessage

                    when (message) {
                        is GraphQLWebsocketMessage.ConnectionInitMessage -> {
                            if (!connectionInitPayloadRef.compareAndSet(null, message.payload)) {
                                webSocketSession.close(CloseCode.TooManyInitialisationRequests.toCloseStatus())
                                    .thenMany(Mono.empty())
                            } else {
                                try {
                                    webSocketInterceptor?.connectionInitialization(message.payload)
                                    Flux.just(
                                        toWebsocketMessage(
                                            GraphQLWebsocketMessage.ConnectionAckMessage(), webSocketSession
                                        )
                                    )
                                } catch (e: Throwable) {
                                    webSocketSession.close(CloseCode.Forbidden.toCloseStatus("Forbidden"))
                                        .thenMany(Mono.empty())
                                }
                            }
                        }

                        is GraphQLWebsocketMessage.PingMessage -> {
                            webSocketInterceptor?.ping(message.payload)
                            Flux.just(
                                toWebsocketMessage(
                                    GraphQLWebsocketMessage.PongMessage(), webSocketSession
                                )
                            )
                        }
                        is GraphQLWebsocketMessage.PongMessage -> {
                            webSocketInterceptor?.pong(message.payload)
                            Flux.empty()
                        }
                        is GraphQLWebsocketMessage.SubscribeMessage -> {
                            if (connectionInitPayloadRef.get() == null) {
                                webSocketSession.close(CloseCode.Unauthorized.toCloseStatus()).thenMany(Mono.empty())
                            } else {
                                dgsReactiveQueryExecutor.execute(
                                    message.payload.query,
                                    message.payload.variables,
                                    message.payload.extensions,
                                    handshakeInfo.headers,
                                    message.payload.operationName,
                                    null
                                )
                                    .flatMapMany { executionResult ->
                                        handleResponse(webSocketSession, message.id, subscriptions, executionResult)
                                    }
                                    .doOnTerminate { subscriptions.remove(message.id) }
                            }
                        }

                        is GraphQLWebsocketMessage.CompleteMessage -> {
                            val id = message.id
                            subscriptions.remove(id)?.cancel()
                            logger.debug(
                                "Client stopped subscription {} for connection {}",
                                id, webSocketSession.id
                            )
                            Flux.empty()
                        }

                        else -> webSocketSession.close(CloseCode.BadRequest.toCloseStatus()).thenMany(Mono.empty())
                    }
                }
        )
    }

    private fun toWebsocketMessage(message: GraphQLWebsocketMessage, session: WebSocketSession): WebSocketMessage {
        return WebSocketMessage(
            WebSocketMessage.Type.TEXT,
            encoder.encodeValue(
                message,
                session.bufferFactory(),
                resolvableType,
                MimeTypeUtils.APPLICATION_JSON,
                null
            )
        )
    }

    private fun handleResponse(
        session: WebSocketSession,
        id: String,
        subscriptions: MutableMap<String, Subscription>,
        executionResult: ExecutionResult
    ): Flux<WebSocketMessage> {
        if (logger.isDebugEnabled) {
            logger.debug(
                "Execution result ready" +
                    (if (!CollectionUtils.isEmpty(executionResult.errors)) " with errors: " + executionResult.errors else "") +
                    "."
            )
        }
        val responseFlux: Flux<Map<String, Any>> = if ((executionResult.getData() as Any) is Publisher<*>) {
            // Subscription
            Flux.from(executionResult.getData() as Publisher<ExecutionResult>)
                .map { obj: ExecutionResult -> obj.toSpecification() }
                .doOnSubscribe { subscription: Subscription ->
                    val previous = subscriptions.putIfAbsent(id, subscription)
                    if (previous != null) {
                        throw SubscriptionExistsException()
                    }
                }
        } else {
            // Single response (query or mutation) that may contain errors
            Flux.just(executionResult.getData())
        }
        return responseFlux
            .map {
                val next = GraphQLWebsocketMessage.NextMessage(payload = executionResult, id = id)
                toWebsocketMessage(next, session)
            }
            .onErrorResume { ex: Throwable? ->
                if (ex is SubscriptionExistsException) {
                    val status = CloseCode.SubscriberAlreadyExists.toCloseStatus("Subscriber for $id already exists")
                    session.close(status).thenMany(Mono.empty())
                } else {
                    Mono.fromCallable {
                        val error = GraphqlErrorBuilder.newError().message(ex!!.message).build()
                        toWebsocketMessage(GraphQLWebsocketMessage.ErrorMessage(id, listOf(error)), session)
                    }
                }
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DgsReactiveWebsocketTransport::class.java)
    }

    private class SubscriptionExistsException : RuntimeException()
}

/**
 * `graphql-ws` expected and standard close codes of the [GraphQL over WebSocket Protocol](https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md).
 */
enum class CloseCode(val code: Int) {
    InternalServerError(4500),
    BadRequest(4400),

    /** Tried subscribing before connect ack */
    Unauthorized(4401),
    Forbidden(4403),
    SubprotocolNotAcceptable(4406),
    ConnectionInitialisationTimeout(4408),
    ConnectionAcknowledgementTimeout(4504),

    /** Subscriber distinction is very important */
    SubscriberAlreadyExists(4409),
    TooManyInitialisationRequests(4429);

    fun toCloseStatus(reason: String? = null): CloseStatus {
        return CloseStatus(code, reason)
    }
}
