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

package com.netflix.graphql.dgs.webflux.handlers

import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import com.netflix.graphql.types.subscription.GRAPHQL_SUBSCRIPTIONS_TRANSPORT_WS_PROTOCOL
import com.netflix.graphql.types.subscription.GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import java.time.Duration

class DgsReactiveWebsocketHandler(dgsReactiveQueryExecutor: DgsReactiveQueryExecutor, connectionInitTimeout: Duration) : WebSocketHandler {

    private val graphqlWSHandler = WebsocketGraphQLWSProtocolHandler(dgsReactiveQueryExecutor)
    private val graphqlTransportWSHandler = WebsocketGraphQLTransportWSProtocolHandler(dgsReactiveQueryExecutor, connectionInitTimeout)
    override fun getSubProtocols(): List<String> = listOf(GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL, GRAPHQL_SUBSCRIPTIONS_TRANSPORT_WS_PROTOCOL)

    override fun handle(webSocketSession: WebSocketSession): Mono<Void> {
        if (webSocketSession.handshakeInfo.subProtocol.equals(GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL, ignoreCase = true)) {
            return graphqlWSHandler.handle(webSocketSession)
        } else if (webSocketSession.handshakeInfo.subProtocol.equals(GRAPHQL_SUBSCRIPTIONS_TRANSPORT_WS_PROTOCOL, ignoreCase = true)) {
            return graphqlTransportWSHandler.handle(webSocketSession)
        }

        return Mono.empty()
    }
}
