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

package com.netflix.graphql.dgs.webflux.handlers

import com.netflix.graphql.dgs.transports.websockets.GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL
import com.netflix.graphql.dgs.transports.websockets.GRAPHQL_TRANSPORT_WS_PROTOCOL
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono

class DgsCompatibleWebsocketHandler(
    private val graphqlWs: WebSocketHandler, // graphql-ws
    private val subTransWs: WebSocketHandler // subscriptions-transport-ws
) : WebSocketHandler {
    override fun handle(session: WebSocketSession): Mono<Void> {
        return when (session.handshakeInfo.subProtocol) {
            GRAPHQL_TRANSPORT_WS_PROTOCOL -> graphqlWs.handle(session)
            GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL -> subTransWs.handle(session)
            else -> {
                // graphql-ws will welcome its own subprotocol and
                // gracefully reject invalid ones. if the client supports
                // both transports, graphql-ws will prevail
                graphqlWs.handle(session)
            }
        }
    }
}
