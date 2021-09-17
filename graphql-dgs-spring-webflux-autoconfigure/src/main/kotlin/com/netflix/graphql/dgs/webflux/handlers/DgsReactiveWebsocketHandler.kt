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

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import org.reactivestreams.Publisher
import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import org.springframework.core.ResolvableType
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.util.MimeTypeUtils
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

class DgsReactiveWebsocketHandler(private val dgsReactiveQueryExecutor: DgsReactiveQueryExecutor) : WebSocketHandler {

    private val resolvableType = ResolvableType.forType(OperationMessage::class.java)
    private val subscriptions = ConcurrentHashMap<String, MutableMap<String, Subscription>>()
    private val decoder = Jackson2JsonDecoder()
    private val encoder = Jackson2JsonEncoder(decoder.objectMapper)

    override fun getSubProtocols(): List<String> = listOf("graphql-ws")

    override fun handle(webSocketSession: WebSocketSession): Mono<Void> {
        return webSocketSession.send(
            webSocketSession.receive()
                .flatMap { message ->
                    val buffer: DataBuffer = DataBufferUtils.retain(message.payload)

                    val operationMessage: OperationMessage = decoder.decode(
                        buffer,
                        resolvableType,
                        MimeTypeUtils.APPLICATION_JSON,
                        null
                    ) as OperationMessage

                    when (operationMessage.type) {
                        GQL_CONNECTION_INIT -> Flux.just(
                            toWebsocketMessage(
                                OperationMessage(GQL_CONNECTION_ACK), webSocketSession
                            )
                        )
                        GQL_START -> {
                            val queryPayload = decoder.objectMapper.convertValue<QueryPayload>(
                                operationMessage.payload ?: error("payload == null")
                            )
                            logger.debug("Starting subscription {} for session {}", queryPayload, webSocketSession.id)
                            dgsReactiveQueryExecutor.execute(queryPayload.query, queryPayload.variables)
                                .flatMapMany { executionResult ->
                                    val publisher: Publisher<Any> = executionResult.getData()
                                    Flux.from(publisher).map {
                                        toWebsocketMessage(
                                            OperationMessage(GQL_DATA, it, operationMessage.id),
                                            webSocketSession
                                        )
                                    }.doOnSubscribe {
                                        if (operationMessage.id != null) {
                                            subscriptions[webSocketSession.id] = mutableMapOf(operationMessage.id to it)
                                        }
                                    }.doOnComplete {
                                        webSocketSession.send(
                                            Flux.just(
                                                toWebsocketMessage(
                                                    OperationMessage(GQL_COMPLETE, null, operationMessage.id),
                                                    webSocketSession
                                                )
                                            )
                                        ).subscribe()

                                        subscriptions[webSocketSession.id]?.remove(operationMessage.id)
                                        logger.debug(
                                            "Completing subscription {} for connection {}",
                                            operationMessage.id, webSocketSession.id
                                        )
                                    }.doOnError {
                                        webSocketSession.send(
                                            Flux.just(
                                                toWebsocketMessage(
                                                    OperationMessage(GQL_ERROR, DataPayload(null, listOf(it.message!!)), operationMessage.id),
                                                    webSocketSession
                                                )
                                            )
                                        ).subscribe()

                                        subscriptions[webSocketSession.id]?.remove(operationMessage.id)
                                        logger.debug(
                                            "Subscription publisher error for input {} for subscription {} for connection {}",
                                            queryPayload, operationMessage.id, webSocketSession.id, it
                                        )
                                    }
                                }
                        }

                        GQL_STOP -> {
                            subscriptions[webSocketSession.id]?.remove(operationMessage.id)?.cancel()
                            logger.debug(
                                "Client stopped subscription {} for connection {}",
                                operationMessage.id, webSocketSession.id
                            )
                            Flux.empty()
                        }

                        GQL_CONNECTION_TERMINATE -> {
                            subscriptions[webSocketSession.id]?.values?.forEach { it.cancel() }
                            subscriptions.remove(webSocketSession.id)
                            webSocketSession.close()
                            logger.debug("Connection {} terminated", webSocketSession.id)
                            Flux.empty()
                        }

                        else -> Flux.empty()
                    }
                }
        )
    }

    private fun toWebsocketMessage(operationMessage: OperationMessage, session: WebSocketSession): WebSocketMessage {
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

        const val GQL_CONNECTION_INIT = "connection_init"
        const val GQL_CONNECTION_ACK = "connection_ack"
        const val GQL_START = "start"
        const val GQL_STOP = "stop"
        const val GQL_DATA = "data"
        const val GQL_ERROR = "error"
        const val GQL_COMPLETE = "complete"
        const val GQL_CONNECTION_TERMINATE = "connection_terminate"
    }
}

data class DataPayload(val data: Any?, val errors: List<Any>? = emptyList())
data class OperationMessage(
    @JsonProperty("type") val type: String,
    @JsonProperty("payload") val payload: Any? = null,
    @JsonProperty("id", required = false) val id: String? = ""
)

data class QueryPayload(
    @JsonProperty("variables") val variables: Map<String, Any> = emptyMap(),
    @JsonProperty("extensions") val extensions: Map<String, Any> = emptyMap(),
    @JsonProperty("operationName") val operationName: String?,
    @JsonProperty("query") val query: String
)
