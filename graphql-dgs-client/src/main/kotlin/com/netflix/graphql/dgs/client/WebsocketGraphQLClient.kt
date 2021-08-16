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

package com.netflix.graphql.dgs.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import graphql.GraphQLException
import org.reactivestreams.Publisher
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.client.WebSocketClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.net.URI

/**
 * Reactive client implementation using websockets and the subscription-transport-ws protocol:
 * https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 */
class WebsocketGraphQLClient(
    private val url: String,
    private val client: WebSocketClient): ReactiveGraphQLClient {
    companion object {
        private val CONNECTION_INIT_MESSAGE = OperationMessage(GQL_CONNECTION_INIT, null, null)
        private val CONNECTION_TERMINATE_MESSAGE = OperationMessage(GQL_CONNECTION_TERMINATE, null, null)
        private val MAPPER = jacksonObjectMapper()
    }

    // Sinks are used as buffers, incoming messages from the server are
    // buffered in incomingSink before being consumed. Outgoing messages
    // for the server are buffered in outgoingSink before being sent.
    private val incomingSink = Sinks
        .many()
        .unicast()
        .onBackpressureBuffer<OperationMessage>()
    private val outgoingSink = Sinks
        .many()
        .unicast()
        .onBackpressureBuffer<OperationMessage>()
    private val conn = Mono.defer {
        val uri = URI(url)
        client.execute(uri) { exchange(incomingSink, outgoingSink, it) }
    }

    override fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
    ): Publisher<GraphQLResponse> {
        return reactiveExecuteQuery(query, variables, null)
    }

    override fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): Publisher<GraphQLResponse> {
        val queryMessage = OperationMessage(
            GQL_START,
            QueryPayload(variables, emptyMap(), operationName, query),
            "1"
        )
        val stopMessage = OperationMessage(GQL_STOP, null, "1")

        // Connect to the server and link the connection to the sinks, this is
        // done immediately and not deferred until subscription

        return Flux.defer {
            val messageStream = incomingSink
                .asFlux()
                .takeUntil { it.type == GQL_COMPLETE }
                .doOnCancel {
                    outgoingSink.tryEmitNext(stopMessage)
                }
                .flatMap(this::handleMessage)

            // First send the handshake message, then the query
            // TODO: Should await acknowledgement message before sending query
            outgoingSink.tryEmitNext(CONNECTION_INIT_MESSAGE)
            outgoingSink.tryEmitNext(queryMessage)

            conn.subscribe()

            messageStream
        }
    }

    fun exchange(
        incomingSink: Sinks.Many<OperationMessage>,
        outgoingSink: Sinks.Many<OperationMessage>,
        session: WebSocketSession): Mono<Void> {
        val incomingMessages = session
            .receive()
            .map(this::decodeMessage)
            .doOnNext(incomingSink::tryEmitNext)
            .doOnComplete(incomingSink::tryEmitComplete)

        val outgoingMessages = session
            .send(outgoingSink
                .asFlux()
                .map{ createMessage(session, it) })

        return Flux
            .merge(incomingMessages, outgoingMessages)
            .then()
    }

    private fun createMessage(
        session: WebSocketSession,
        message: OperationMessage): WebSocketMessage {

        return session.textMessage(MAPPER.writeValueAsString(message))
    }

    private fun decodeMessage(message: WebSocketMessage): OperationMessage {
        val messageText = message.payloadAsText;
        val type = object : TypeReference<OperationMessage>() {}

        return MAPPER.readValue(messageText, type)
    }

    private fun handleMessage(
        message: OperationMessage): Flux<GraphQLResponse> {
        when(message.type) {
            // Do nothing if no data provided
            GQL_CONNECTION_ACK, GQL_CONNECTION_KEEP_ALIVE, GQL_COMPLETE -> {
                return Flux.empty()
            }
            // Convert data to GraphQLResponse
            GQL_DATA -> {
                val payload = message.payload
                // Payload can be either QueryPayload or DataPayload
                // TODO: Typecheck this?
                if (payload is DataPayload)
                    return Flux.just(GraphQLResponse(MAPPER.writeValueAsString(payload)))
                else
                    throw GraphQLException("Message $message has type " +
                            "GQL_DATA but not a valid data payload")
            }
            // Convert errors received from the server into exceptions, does
            // not include GraphQL execution errors which are bundled in the
            // GraphQLResponse above.
            GQL_CONNECTION_ERROR, GQL_ERROR -> {
                val errorMessage = message.payload.toString()
                throw GraphQLException(errorMessage)
            }
            // The message is invalid according to the subscriptions transport
            // protocol so it should result in an exception
            else -> {
                throw GraphQLException("Unable to handle message of type " +
                        "${message.type}. Full message: $message")
            }
        }
    }
}


const val GQL_CONNECTION_INIT = "connection_init"
const val GQL_CONNECTION_ACK = "connection_ack"
const val GQL_CONNECTION_ERROR = "connection_error"
const val GQL_START = "start"
const val GQL_STOP = "stop"
const val GQL_DATA = "data"
const val GQL_ERROR = "error"
const val GQL_COMPLETE = "complete"
const val GQL_CONNECTION_TERMINATE = "connection_terminate"
const val GQL_CONNECTION_KEEP_ALIVE = "ka"

data class OperationMessage(
    @JsonProperty("type")
    val type: String,
    @JsonProperty("payload")
    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    @JsonSubTypes(
        JsonSubTypes.Type(value = DataPayload::class),
        JsonSubTypes.Type(value = QueryPayload::class))
    val payload: Any? = null,
    @JsonProperty("id", required = false)
    val id: String? = "")

data class DataPayload(
    @JsonProperty("data")
    val data: Any?,
    @JsonProperty("errors")
    val errors: List<Any>? = emptyList())

data class QueryPayload(
    @JsonProperty("variables")
    val variables: Map<String, Any>?,
    @JsonProperty("extensions")
    val extensions: Map<String, Any> = emptyMap(),
    @JsonProperty("operationName")
    val operationName: String?,
    @JsonProperty("query")
    val query: String)


