/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.graphql.dgs.subscriptions.websockets

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import graphql.ExecutionResult
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.annotation.PostConstruct

class DgsWebSocketHandler(private val dgsQueryExecutor: DgsQueryExecutor) : TextWebSocketHandler() {
    private val logger = LoggerFactory.getLogger(DgsWebSocketHandler::class.java)
    internal val subscriptions = ConcurrentHashMap<String, MutableMap<String, Subscription>>()
    internal val sessions = CopyOnWriteArrayList<WebSocketSession>()

    @PostConstruct
    fun setupCleanup() {
        val timerTask = object : TimerTask() {
            override fun run() {
                sessions.filter { !it.isOpen }.forEach(this@DgsWebSocketHandler::cleanupSubscriptionsForSession)
            }
        }

        val timer = Timer(true)
        timer.scheduleAtFixedRate(timerTask, 0, 5000)
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val (type, payload, id) = jacksonObjectMapper().readValue(message.payload, OperationMessage::class.java)
        when (type) {
            GQL_CONNECTION_INIT -> {
                logger.info("Initialized connection for {}", session.id)
                sessions.add(session)
                session.sendMessage(
                    TextMessage(
                        jacksonObjectMapper().writeValueAsBytes(
                            OperationMessage(
                                GQL_CONNECTION_ACK
                            )
                        )
                    )
                )
            }
            GQL_START -> {
                val queryPayload = jacksonObjectMapper().convertValue(payload, QueryPayload::class.java)
                handleSubscription(id!!, queryPayload, session)
            }
            GQL_STOP -> {
                subscriptions[session.id]?.get(id)?.cancel()
                subscriptions.remove(id)
            }
            GQL_CONNECTION_TERMINATE -> {
                logger.info("Terminated session " + session.id)
                cleanupSubscriptionsForSession(session)
                subscriptions.remove(session.id)
                session.close()
            }
            else -> session.sendMessage(TextMessage(jacksonObjectMapper().writeValueAsBytes(OperationMessage("error"))))
        }
    }

    private fun cleanupSubscriptionsForSession(session: WebSocketSession) {
        logger.info("Cleaning up for session {}", session.id)
        subscriptions[session.id]?.values?.forEach { it.cancel() }
        sessions.remove(session)
    }

    private fun handleSubscription(id: String, payload: QueryPayload, session: WebSocketSession) {
        val executionResult: ExecutionResult = dgsQueryExecutor.execute(payload.query)
        val subscriptionStream: Publisher<ExecutionResult> = executionResult.getData()

        subscriptionStream.subscribe(object : Subscriber<ExecutionResult> {
            override fun onSubscribe(s: Subscription) {
                logger.info("Subscription started for {}", id)
                subscriptions[session.id] = mutableMapOf(Pair(id, s))

                s.request(1)
            }

            override fun onNext(er: ExecutionResult) {
                val message = OperationMessage(GQL_DATA, DataPayload(er.getData()), id)
                val jsonMessage = TextMessage(jacksonObjectMapper().writeValueAsBytes(message))
                logger.debug("Sending subscription data: {}", jsonMessage)

                if (session.isOpen) {
                    session.sendMessage(jsonMessage)
                    subscriptions[session.id]?.get(id)?.request(1)
                }
            }

            override fun onError(t: Throwable) {
                logger.error("Error on subscription {}", id, t)
                val message = OperationMessage(GQL_ERROR, DataPayload(null, listOf(t.message!!)), id)
                val jsonMessage = TextMessage(jacksonObjectMapper().writeValueAsBytes(message))
                logger.debug("Sending subscription error: {}", jsonMessage)

                if (session.isOpen) {
                    session.sendMessage(jsonMessage)
                }
            }

            override fun onComplete() {
                logger.info("Subscription completed for {}", id)
                val message = OperationMessage(GQL_TYPE, null, id)
                val jsonMessage = TextMessage(jacksonObjectMapper().writeValueAsBytes(message))

                if (session.isOpen) {
                    session.sendMessage(jsonMessage)
                }

                subscriptions.remove(id)
            }
        })
    }
}

const val GQL_CONNECTION_INIT = "connection_init"
const val GQL_CONNECTION_ACK = "connection_ack"
const val GQL_START = "start"
const val GQL_STOP = "stop"
const val GQL_DATA = "data"
const val GQL_ERROR = "error"
const val GQL_TYPE = "complete"
const val GQL_CONNECTION_TERMINATE = "connection_terminate"

data class DataPayload(val data: Any?, val errors: List<Any>? = emptyList())
data class OperationMessage(@JsonProperty("type") val type: String, @JsonProperty("payload") val payload: Any? = null, @JsonProperty("id", required = false) val id: String? = "")
data class QueryPayload(@JsonProperty("variables") val variables: Map<String, Any> = emptyMap(), @JsonProperty("extensions") val extensions: Map<String, Any> = emptyMap(), @JsonProperty("operationName") val operationName: String?, @JsonProperty("query") val query: String)
