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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.types.subscription.*
import graphql.ExecutionResult
import jakarta.annotation.PostConstruct
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class WebsocketGraphQLWSProtocolHandler(
    private val dgsQueryExecutor: DgsQueryExecutor,
    private val subscriptionErrorLogLevel: Level,
    private val objectMapper: ObjectMapper,
) : TextWebSocketHandler() {

    internal val subscriptions = ConcurrentHashMap<String, MutableMap<String, Subscription>>()
    internal val sessions = CopyOnWriteArrayList<WebSocketSession>()

    @PostConstruct
    fun setupCleanup() {
        val timerTask = object : TimerTask() {
            override fun run() {
                sessions.filter { !it.isOpen }.forEach(this@WebsocketGraphQLWSProtocolHandler::cleanupSubscriptionsForSession)
            }
        }

        val timer = Timer(true)
        timer.scheduleAtFixedRate(timerTask, 0, 5000)
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val (type, payload, id) = objectMapper.readValue(message.payload, OperationMessage::class.java)
        when (type) {
            GQL_CONNECTION_INIT -> {
                logger.info("Initialized connection for {}", session.id)
                sessions.add(session)
                session.sendMessage(
                    TextMessage(
                        objectMapper.writeValueAsBytes(
                            OperationMessage(
                                GQL_CONNECTION_ACK
                            )
                        )
                    )
                )
            }
            GQL_START -> {
                val queryPayload = objectMapper.convertValue(payload, QueryPayload::class.java)
                handleSubscription(id!!, queryPayload, session)
            }
            GQL_STOP -> {
                subscriptions[session.id]?.get(id)?.cancel()
                subscriptions[session.id]?.remove(id)
            }
            GQL_CONNECTION_TERMINATE -> {
                logger.info("Terminated session {}", session.id)
                cleanupSubscriptionsForSession(session)
                session.close()
            }
            else -> session.sendMessage(TextMessage(objectMapper.writeValueAsBytes(OperationMessage("error"))))
        }
    }

    private fun cleanupSubscriptionsForSession(session: WebSocketSession) {
        logger.info("Cleaning up for session {}", session.id)
        subscriptions[session.id]?.values?.forEach { it.cancel() }
        subscriptions.remove(session.id)
        sessions.remove(session)
    }

    private fun handleSubscription(id: String, payload: QueryPayload, session: WebSocketSession) {
        val executionResult: ExecutionResult = dgsQueryExecutor.execute(payload.query, payload.variables.orEmpty())
        val subscriptionStream: Publisher<ExecutionResult> = executionResult.getData()

        subscriptionStream.subscribe(object : Subscriber<ExecutionResult> {
            override fun onSubscribe(s: Subscription) {
                logger.info("Subscription started for {}", id)
                subscriptions.putIfAbsent(session.id, mutableMapOf())
                subscriptions[session.id]?.set(id, s)

                s.request(1)
            }

            override fun onNext(er: ExecutionResult) {
                val message = OperationMessage(GQL_DATA, DataPayload(er.getData(), er.errors), id)
                val jsonMessage = TextMessage(objectMapper.writeValueAsBytes(message))
                logger.debug("Sending subscription data: {}", jsonMessage)

                if (session.isOpen) {
                    session.sendMessage(jsonMessage)
                    subscriptions[session.id]?.get(id)?.request(1)
                }
            }

            override fun onError(t: Throwable) {
                when (subscriptionErrorLogLevel) {
                    Level.ERROR -> logger.error("Error on subscription {}", id, t)
                    Level.WARN -> logger.warn("Error on subscription {}", id, t)
                    Level.INFO -> logger.info("Error on subscription {}: {}", id, t.message)
                    Level.DEBUG -> logger.debug("Error on subscription {}", id, t)
                    Level.TRACE -> logger.trace("Error on subscription {}", id, t)
                }
                val message = OperationMessage(GQL_ERROR, DataPayload(null, listOf(t.message!!)), id)
                val jsonMessage = TextMessage(objectMapper.writeValueAsBytes(message))
                logger.debug("Sending subscription error: {}", jsonMessage)

                if (session.isOpen) {
                    session.sendMessage(jsonMessage)
                }
            }

            override fun onComplete() {
                logger.info("Subscription completed for {}", id)
                val message = OperationMessage(GQL_COMPLETE, null, id)
                val jsonMessage = TextMessage(objectMapper.writeValueAsBytes(message))

                if (session.isOpen) {
                    session.sendMessage(jsonMessage)
                }

                subscriptions[session.id]?.remove(id)
            }
        })
    }

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(WebsocketGraphQLWSProtocolHandler::class.java)
    }
}
