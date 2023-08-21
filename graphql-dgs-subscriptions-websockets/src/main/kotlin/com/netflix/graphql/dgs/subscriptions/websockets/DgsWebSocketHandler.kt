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
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.types.subscription.*
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.util.ClassUtils
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.SubProtocolCapable
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.time.Duration

class DgsWebSocketHandler(
    dgsQueryExecutor: DgsQueryExecutor,
    connectionInitTimeout: Duration,
    subscriptionErrorLogLevel: Level,
    objectMapper: ObjectMapper = jacksonObjectMapper()
) : TextWebSocketHandler(), SubProtocolCapable {

    private val graphqlWSHandler = WebsocketGraphQLWSProtocolHandler(dgsQueryExecutor, subscriptionErrorLogLevel, objectMapper)
    private val graphqlTransportWSHandler = WebsocketGraphQLTransportWSProtocolHandler(dgsQueryExecutor, connectionInitTimeout, subscriptionErrorLogLevel, objectMapper)

    @PostConstruct
    fun setupCleanup() {
        try {
            graphqlWSHandler.setupCleanup()
        } catch (e: Exception) {
            logger.error("Error setting up cleanup subscriptions tasks")
        }
        try {
            graphqlTransportWSHandler.setupCleanup()
        } catch (e: Exception) {
            logger.error("Error setting up cleanup subscriptions tasks")
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        try {
            graphqlWSHandler.afterConnectionEstablished(session)
        } catch (e: Exception) {
            logger.error("Unable to handle connection established for ${session.id}")
        }

        try {
            graphqlTransportWSHandler.afterConnectionEstablished(session)
        } catch (e: Exception) {
            logger.error("Unable to handle connection established for ${session.id}")
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        try {
            graphqlWSHandler.afterConnectionClosed(session, status)
        } catch (e: Exception) {
            logger.error("Error closing connection for session ${session.id}")
        }

        try {
            graphqlTransportWSHandler.afterConnectionClosed(session, status)
        } catch (e: Exception) {
            logger.error("Error closing connection for session ${session.id}")
        }
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        loadSecurityContextFromSession(session)
        if (session.acceptedProtocol.equals(GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL, ignoreCase = true)) {
            return graphqlWSHandler.handleTextMessage(session, message)
        } else if (session.acceptedProtocol.equals(GRAPHQL_SUBSCRIPTIONS_TRANSPORT_WS_PROTOCOL, ignoreCase = true)) {
            return graphqlTransportWSHandler.handleTextMessage(session, message)
        }
    }

    private fun loadSecurityContextFromSession(session: WebSocketSession) {
        if (springSecurityAvailable) {
            val securityContext = session.attributes["SPRING_SECURITY_CONTEXT"] as? SecurityContext
            if (securityContext != null) {
                SecurityContextHolder.setContext(securityContext)
            }
        }
    }

    override fun getSubProtocols(): List<String> = listOf(GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL, GRAPHQL_SUBSCRIPTIONS_TRANSPORT_WS_PROTOCOL)

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(DgsWebSocketHandler::class.java)

        private val springSecurityAvailable: Boolean = ClassUtils.isPresent(
            "org.springframework.security.core.context.SecurityContextHolder",
            DgsWebSocketHandler::class.java.classLoader
        )
    }
}
