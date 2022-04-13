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

package com.netflix.graphql.dgs.transports.websockets

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.internal.utils.TimeTracer
import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.GraphqlErrorBuilder
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.SubProtocolCapable
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.annotation.PostConstruct

/**
 * WebSocketHandler for GraphQL based on
 * <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL Over WebSocket Protocol</a> and
 * for use in DGS framework.
 */
class DgsWebsocketTransport(
    private val dgsQueryExecutor: DgsQueryExecutor,
    private val webSocketInterceptor: WebSocketInterceptor? = null,
) :
    TextWebSocketHandler(), SubProtocolCapable {

    internal val sessions = CopyOnWriteArrayList<WebSocketSession>()
    internal val contexts = ConcurrentHashMap<String, Context<Any>>()

    @PostConstruct
    fun setupCleanup() {
        val timerTask = object : TimerTask() {
            override fun run() {
                sessions.filter { !it.isOpen }
                    .forEach(this@DgsWebsocketTransport::cleanupSubscriptionsForSession)
            }
        }

        val timer = Timer(true)
        timer.scheduleAtFixedRate(timerTask, 0, 5000)
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        contexts[session.id] = Context()
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
                    return session.close(CloseCode.BadRequest.toCloseStatus("Too many initialisation requests"))
                }
                sessions.add(session)

                context.connectionParams = message.payload
                try {
                    webSocketInterceptor?.connectionInitialization(message.payload)

                    session.sendMessage(
                        TextMessage(
                            objectMapper.writeValueAsBytes(
                                Message.ConnectionAckMessage(
                                    payload = null
                                )
                            )
                        )
                    )
                    context.acknowledged = true
                } catch (e: Throwable) {
                    session.close(CloseCode.Forbidden.toCloseStatus("Forbidden"))
                }
            }
            is Message.PingMessage -> {
                webSocketInterceptor?.ping(message.payload)
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
                webSocketInterceptor?.pong(message.payload)
            }
            is Message.SubscribeMessage -> {
                if (!context.acknowledged) {
                    return session.close(CloseCode.Unauthorized.toCloseStatus("Unauthorized"))
                }
                val (id, payload) = message
                if (context.subscriptions.contains(id)) {
                    return session.close(CloseCode.SubscriberAlreadyExists.toCloseStatus("Subscriber for $id already exists"))
                }

                handleSubscription(id, payload, session)
            }
            is Message.CompleteMessage -> {
                webSocketInterceptor?.connectionCompletion()
                logger.info("Complete subscription for " + message.id)
                val subscription = context.subscriptions.remove(message.id)
                subscription?.cancel()
            }
            else -> session.close(CloseCode.BadRequest.toCloseStatus())
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
        val executionResult: ExecutionResult = TimeTracer.logTime(
            {
                dgsQueryExecutor.execute(
                    payload.query,
                    payload.variables,
                    payload.extensions,
                    null,
                    payload.operationName,
                    null
                )
            }, logger, "Executed query in {}ms"
        )
        if (logger.isDebugEnabled) {
            logger.debug(
                "Execution result - Contains data: '{}' - Number of errors: {}",
                executionResult.isDataPresent,
                executionResult.errors.size
            )
        }
        val result = try {
            TimeTracer.logTime(
                { objectMapper.writeValueAsString(executionResult.toSpecification()) },
                logger,
                "Serialized JSON result in {}ms"
            )
        } catch (ex: InvalidDefinitionException) {
            val errorMessage = "Error serializing response: ${ex.message}"
            val errorResponse = ExecutionResultImpl(GraphqlErrorBuilder.newError().message(errorMessage).build())
            logger.error(errorMessage, ex)
            objectMapper.writeValueAsString(errorResponse.toSpecification())
        }

        val data = executionResult.getData<Any>()

        if (data is Publisher<*>) {
            val subscriptionStream: Publisher<ExecutionResult> = data as Publisher<ExecutionResult>

            subscriptionStream.subscribe(object : Subscriber<ExecutionResult> {
                override fun onSubscribe(s: Subscription) {
                    logger.info("Subscription started for {}", id)
                    contexts[session.id]?.subscriptions?.set(id, s)

                    s.request(1)
                }

                override fun onNext(er: ExecutionResult) {
                    val message = Message.NextMessage(payload = er, id = id)
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
        } else {
            val next = Message.NextMessage(payload = executionResult, id = id)
            val nextMessage = TextMessage(objectMapper.writeValueAsBytes(next))
            logger.debug("Sending subscription data: {}", nextMessage)

            if (session.isOpen) {
                session.sendMessage(nextMessage)
                contexts[session.id]?.subscriptions?.get(id)?.request(1)
            }
            val complete = Message.CompleteMessage(id)
            val completeMessage = TextMessage(objectMapper.writeValueAsBytes(complete))

            if (session.isOpen) {
                session.sendMessage(completeMessage)
            }
            contexts[session.id]?.subscriptions?.remove(id)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(DgsWebsocketTransport::class.java)
        val objectMapper = jacksonObjectMapper()
        val subProtocolList = listOf(GRAPHQL_TRANSPORT_WS_PROTOCOL)
    }

    var WebSocketSession.connectionInitReceived: Boolean
        get() = attributes["connectionInitReceived"] == true
        set(value) {
            attributes["connectionInitReceived"] = value
        }

    override fun getSubProtocols() = subProtocolList
}
