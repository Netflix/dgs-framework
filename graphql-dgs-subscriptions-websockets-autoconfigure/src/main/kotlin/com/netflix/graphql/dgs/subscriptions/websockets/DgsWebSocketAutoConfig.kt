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
import com.netflix.graphql.dgs.transports.websockets.DgsWebsocketTransport
import com.netflix.graphql.dgs.transports.websockets.GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL
import com.netflix.graphql.dgs.transports.websockets.GRAPHQL_TRANSPORT_WS_PROTOCOL
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.support.DefaultHandshakeHandler

@Configuration
@ConditionalOnWebApplication
open class DgsWebSocketAutoConfig {
    @Bean
    @Qualifier("graphql-ws")
    open fun subTransWs(@Suppress("SpringJavaInjectionPointsAutowiringInspection") dgsQueryExecutor: DgsQueryExecutor): WebSocketHandler {
        return DgsWebSocketHandler(dgsQueryExecutor)
    }

    @Bean
    @Qualifier("graphql-transport-ws")
    open fun graphqlWs(@Suppress("SpringJavaInjectionPointsAutowiringInspection") dgsQueryExecutor: DgsQueryExecutor): WebSocketHandler {
        return DgsWebsocketTransport(dgsQueryExecutor)
    }

    @Bean
    open fun webSocketHandler(
        @Qualifier("graphql-ws") subTransWs: WebSocketHandler,
        @Qualifier("graphql-transport-ws") graphqlWs: WebSocketHandler,
    ): WebSocketHandler {
        return DgsCompatibleWebsocketHandler(graphqlWs, subTransWs)
    }

    @Configuration
    @EnableWebSocket
    internal open class WebSocketConfig(@Suppress("SpringJavaInjectionPointsAutowiringInspection") private val webSocketHandler: WebSocketHandler) :
        WebSocketConfigurer {

        override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
            val defaultHandshakeHandler = DefaultHandshakeHandler()
            defaultHandshakeHandler.setSupportedProtocols(
                GRAPHQL_TRANSPORT_WS_PROTOCOL,
                GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL
            )
            registry.addHandler(webSocketHandler, "/subscriptions").setHandshakeHandler(defaultHandshakeHandler)
                .setAllowedOrigins("*")
        }
    }
}
