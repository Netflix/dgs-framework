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

import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.types.subscription.*
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.SubProtocolCapable
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.time.Duration
import java.util.*

class DgsWebSocketHandler(dgsQueryExecutor: DgsQueryExecutor, connectionInitTimeout: Duration) : TextWebSocketHandler(), SubProtocolCapable {

    private val graphqlWSHandler = WebsocketGraphQLWSProtocolHandler(dgsQueryExecutor)
    private val graphqlTransportWSHandler = WebsocketGraphQLTransportWSProtocolHandler(dgsQueryExecutor, connectionInitTimeout)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        graphqlWSHandler.afterConnectionEstablished(session)
        graphqlTransportWSHandler.afterConnectionEstablished(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        graphqlWSHandler.afterConnectionEstablished(session)
        graphqlTransportWSHandler.afterConnectionEstablished(session)
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        if (session.acceptedProtocol.equals(GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL, ignoreCase = true)) {
            return graphqlWSHandler.handleTextMessage(session, message)
        } else if (session.acceptedProtocol.equals(GRAPHQL_SUBSCRIPTIONS_TRANSPORT_WS_PROTOCOL, ignoreCase = true)) {
            return graphqlTransportWSHandler.handleTextMessage(session, message)
        }
    }

    override fun getSubProtocols(): List<String> = listOf(GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL, GRAPHQL_SUBSCRIPTIONS_TRANSPORT_WS_PROTOCOL)
}
