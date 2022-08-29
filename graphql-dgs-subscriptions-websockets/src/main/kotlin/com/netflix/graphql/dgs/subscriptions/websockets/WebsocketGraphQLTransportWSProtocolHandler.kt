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

package com.netflix.graphql.dgs.subscriptions.websockets

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.types.subscription.websockets.CloseCode
import com.netflix.graphql.types.subscription.websockets.Message
import graphql.ExecutionResult
import graphql.GraphqlErrorBuilder
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.annotation.PostConstruct

/**
 * WebSocketHandler for GraphQL based on
 * <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL Over WebSocket Protocol</a> and
 * for use in DGS framework.
 */
class WebsocketGraphQLTransportWSProtocolHandler(private val dgsQueryExecutor: DgsQueryExecutor, private val connectionInitTimeout: Duration) : TextWebSocketHandler() {

    internal val sessions = CopyOnWriteArrayList<WebSocketSession>()
    internal val contexts = ConcurrentHashMap<String, Context<Any>>()

    @PostConstruct
    fun setupCleanup() {
        val timerTask = object : TimerTask() {
            override fun run() {
                sessions.filter { !it.isOpen }
                    .forEach(this@WebsocketGraphQLTransportWSProtocolHandler::cleanupSubscriptionsForSession)
            }
        }

        val timer = Timer(true)
        timer.scheduleAtFixedRate(timerTask, 0, 5000)
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        contexts[session.id] = Context()
        val timerTask = object : TimerTask() {
            override fun run() {
                if (contexts[session.id]?.getConnectionInitReceived() == false) {
                    session.close(CloseStatus(CloseCode.ConnectionInitialisationTimeout.code))
                    contexts.remove(session.id)
                }
            }
        }

        val timer = Timer()
        timer.schedule(timerTask, connectionInitTimeout.toMillis())
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        if (status == CloseStatus.NORMAL) {
            cleanupSubscriptionsForSession(session)
        }
    }

    public override fun handleTextMessage(session: WebSocketSession, textMessage: TextMessage) {
        val message = objectMapper.readValue(textMessage.payload, Message::class.java)
        val context = contexts[session.id]!!

        when (message) {
            is Message.ConnectionInitMessage -> {
                logger.info("Initialized connection for {}", session.id)
                if (context.setConnectionInitReceived()) {
                    return session.close(CloseStatus(CloseCode.BadRequest.code, "Too many initialisation requests"))
                }
                sessions.add(session)

                context.connectionParams = message.payload
                try {

                    session.sendMessage(
                        TextMessage(
                            objectMapper.writeValueAsBytes(
                                Message.ConnectionAckMessage()
                            )
                        )
                    )
                    context.acknowledged = true
                } catch (e: Throwable) {
                    session.close(CloseStatus(CloseCode.Forbidden.code, "Forbidden"))
                }
            }
            is Message.PingMessage -> {
                session.sendMessage(
                    TextMessage(
                        objectMapper.writeValueAsBytes(
                            Message.PongMessage(
                                payload = message.payload
                            )
                        )
                    )
                )
            }
            is Message.PongMessage -> {
            }
            is Message.SubscribeMessage -> {
                if (!context.acknowledged) {
                    return session.close(CloseStatus(CloseCode.Unauthorized.code, "Unauthorized"))
                }
                val (id, payload) = message
                if (context.subscriptions.contains(id)) {
                    return session.close(CloseStatus(CloseCode.SubscriberAlreadyExists.code, "Subscriber for $id already exists"))
                }

                handleSubscription(id, payload, session)
            }
            is Message.CompleteMessage -> {
                logger.info("Complete subscription for " + message.id)
                val subscription = context.subscriptions.remove(message.id)
                subscription?.cancel()
            }
            else -> session.close(CloseStatus(CloseCode.BadRequest.code, "Unexpected message format"))
        }
    }

    private fun cleanupSubscriptionsForSession(session: WebSocketSession) {
        logger.info("Cleaning up for session {}", session.id)
        contexts[session.id]?.subscriptions?.values?.forEach { it.cancel() }
        contexts.remove(session.id)
        sessions.remove(session)
    }

    private fun handleSubscription(
        id: String,
        payload: Message.SubscribeMessage.Payload,
        session: WebSocketSession
    ) {
        val executionResult: ExecutionResult =
            dgsQueryExecutor.execute(
                payload.query,
                payload.variables,
                payload.extensions,
                null,
                payload.operationName,
                null
            )

        val subscriptionStream: Publisher<ExecutionResult> = executionResult.getData()

        subscriptionStream.subscribe(object : Subscriber<ExecutionResult> {
            override fun onSubscribe(s: Subscription) {
                logger.info("Subscription started for {}", id)
                contexts[session.id]?.subscriptions?.set(id, s)

                s.request(1)
            }

            override fun onNext(er: ExecutionResult) {
                val message = Message.NextMessage(
                    payload = com.netflix.graphql.types.subscription.websockets.ExecutionResult(er.getData(), er.errors),
                    id = id
                )
                val jsonMessage = TextMessage(objectMapper.writeValueAsBytes(message))
                logger.debug("Sending subscription data: {}", jsonMessage)

                if (session.isOpen) {
                    session.sendMessage(jsonMessage)
                    contexts[session.id]?.subscriptions?.get(id)?.request(1)
                }
            }

            override fun onError(t: Throwable) {
                logger.error("Error on subscription {}", id, t)

                val message =
                    Message.ErrorMessage(
                        id = id,
                        payload = listOf(GraphqlErrorBuilder.newError().message(t.message).build())
                    )
                val jsonMessage = TextMessage(objectMapper.writeValueAsBytes(message))
                logger.debug("Sending subscription error: {}", jsonMessage)

                if (session.isOpen) {
                    session.sendMessage(jsonMessage)
                }
            }

            override fun onComplete() {
                logger.info("Subscription completed for {}", id)
                val message = Message.CompleteMessage(id)
                val jsonMessage = TextMessage(objectMapper.writeValueAsBytes(message))

                if (session.isOpen) {
                    session.sendMessage(jsonMessage)
                }
                contexts[session.id]?.subscriptions?.remove(id)
            }
        })
    }

    private companion object {
        val logger = LoggerFactory.getLogger(WebsocketGraphQLTransportWSProtocolHandler::class.java)
        val objectMapper = jacksonObjectMapper()
    }
}
