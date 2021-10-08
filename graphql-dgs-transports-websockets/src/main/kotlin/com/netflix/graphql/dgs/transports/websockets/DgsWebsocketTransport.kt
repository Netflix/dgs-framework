package com.netflix.graphql.dgs.transports.websockets

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import graphql.ExecutionResult
import graphql.GraphqlErrorBuilder
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

class DgsWebsocketTransport(
    private val dgsQueryExecutor: DgsQueryExecutor,
) :
    TextWebSocketHandler() {

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

    public override fun handleTextMessage(session: WebSocketSession, textMessage: TextMessage) {
        val message = objectMapper.readValue(textMessage.payload, Message::class.java)
        val context = contexts[session.id]!!

        when (message) {
            is Message.ConnectionInitMessage -> {
                logger.info("Initialized connection for {}", session.id)
                if (context.connectionInitReceived) {
                    return session.close(CloseCode.BadRequest.toCloseStatus("Too many initialisation requests"))
                }
                context.connectionInitReceived = true
                sessions.add(session)

                context.connectionParams = message.payload

                // TODO: onConnect listener

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
                // TODO: onPong
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
                logger.info("Complete subscription for " + message.id)
                val subscription = context.subscriptions[message.id]
                subscription?.cancel()
                context.subscriptions.remove(message.id)
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
    }

    private companion object {
        val logger = LoggerFactory.getLogger(DgsWebsocketTransport::class.java)
        val objectMapper = jacksonObjectMapper()
    }

    var WebSocketSession.connectionInitReceived: Boolean
        get() = attributes["connectionInitReceived"] == true
        set(value) {
            attributes["connectionInitReceived"] = value
        }
}
