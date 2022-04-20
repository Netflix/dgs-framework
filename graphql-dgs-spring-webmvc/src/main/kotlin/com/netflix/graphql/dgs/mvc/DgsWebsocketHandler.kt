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

package com.netflix.graphql.dgs.mvc

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.internal.utils.TimeTracer
import com.netflix.graphql.dgs.transports.websockets.CloseCode
import com.netflix.graphql.dgs.transports.websockets.GRAPHQL_TRANSPORT_WS_PROTOCOL
import com.netflix.graphql.dgs.transports.websockets.GraphQLWebsocketMessage
import com.netflix.graphql.dgs.transports.websockets.WebSocketInterceptor
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
class DgsWebsocketHandler(
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
                    .forEach(this@DgsWebsocketHandler::cleanupSubscriptionsForSession)
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
        val message = objectMapper.readValue(textMessage.payload, GraphQLWebsocketMessage::class.java)
        val context = contexts[session.id]!!

        when (message) {
            is GraphQLWebsocketMessage.ConnectionInitMessage -> {
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
                                GraphQLWebsocketMessage.ConnectionAckMessage(
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
            is GraphQLWebsocketMessage.PingMessage -> {
                webSocketInterceptor?.ping(message.payload)
                session.sendMessage(
                    TextMessage(
                        objectMapper.writeValueAsBytes(
                            GraphQLWebsocketMessage.PongMessage(
                                payload = message.payload
                            )
                        )
                    )
                )
            }
            is GraphQLWebsocketMessage.PongMessage -> {
                webSocketInterceptor?.pong(message.payload)
            }
            is GraphQLWebsocketMessage.SubscribeMessage -> {
                if (!context.acknowledged) {
                    return session.close(CloseCode.Unauthorized.toCloseStatus("Unauthorized"))
                }
                val (id, payload) = message
                if (context.subscriptions.contains(id)) {
                    return session.close(CloseCode.SubscriberAlreadyExists.toCloseStatus("Subscriber for $id already exists"))
                }

                handleSubscription(id, payload, session)
            }
            is GraphQLWebsocketMessage.CompleteMessage -> {
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
        payload: GraphQLWebsocketMessage.SubscribeMessage.Payload,
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
                    val message = GraphQLWebsocketMessage.NextMessage(payload = er, id = id)
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
                        GraphQLWebsocketMessage.ErrorMessage(
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
                    val message = GraphQLWebsocketMessage.CompleteMessage(id)
                    val jsonMessage = TextMessage(objectMapper.writeValueAsBytes(message))

                    if (session.isOpen) {
                        session.sendMessage(jsonMessage)
                    }
                    contexts[session.id]?.subscriptions?.remove(id)
                }
            })
        } else {
            val next = GraphQLWebsocketMessage.NextMessage(payload = executionResult, id = id)
            val nextMessage = TextMessage(objectMapper.writeValueAsBytes(next))
            logger.debug("Sending subscription data: {}", nextMessage)

            if (session.isOpen) {
                session.sendMessage(nextMessage)
                contexts[session.id]?.subscriptions?.get(id)?.request(1)
            }
            val complete = GraphQLWebsocketMessage.CompleteMessage(id)
            val completeMessage = TextMessage(objectMapper.writeValueAsBytes(complete))

            if (session.isOpen) {
                session.sendMessage(completeMessage)
            }
            contexts[session.id]?.subscriptions?.remove(id)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(DgsWebsocketHandler::class.java)
        val objectMapper = jacksonObjectMapper()
        val subProtocolList = listOf(GRAPHQL_TRANSPORT_WS_PROTOCOL)
    }

    var WebSocketSession.connectionInitReceived: Boolean
        get() = attributes["connectionInitReceived"] == true
        set(value) {
            attributes["connectionInitReceived"] = value
        }

    override fun getSubProtocols() = subProtocolList

    internal class Context<T>(
        /**
         * Indicates that the `ConnectionInit` message
         * has been received by the server. If this is
         * `true`, the client wont be kicked off after
         * the wait timeout has passed.
         */
        private var connectionInitReceived: Boolean = false

    ) {
        /**
         * Indicates that the connection was acknowledged
         * by having dispatched the `ConnectionAck` message
         * to the related client.
         */
        var acknowledged: Boolean = false

        /** The parameters passed during the connection initialisation. */
        var connectionParams: Map<String, Any>? = null

        /**
         * Holds the active subscriptions for this context. **All operations**
         * that are taking place are aggregated here. The user is _subscribed_
         * to an operation when waiting for result(s).
         */
        val subscriptions = ConcurrentHashMap<String, Subscription>()

        /**
         * An extra field where you can store your own context values
         * to pass between callbacks.
         */
        var extra: T? = null

        @Synchronized
        fun setConnectionInitReceived(): Boolean {
            val previousValue: Boolean = this.connectionInitReceived
            this.connectionInitReceived = true
            return previousValue
        }

        fun isConnectionInitNotProcessed(): Boolean {
            return !this.connectionInitReceived
        }

        fun dispose() {
            subscriptions.forEach { (_, subscription) ->

                try {
                    subscription.cancel()
                } catch (e: Throwable) {
                    // Ignore and keep on
                }
            }
            this.subscriptions.clear()
        }
    }
}
