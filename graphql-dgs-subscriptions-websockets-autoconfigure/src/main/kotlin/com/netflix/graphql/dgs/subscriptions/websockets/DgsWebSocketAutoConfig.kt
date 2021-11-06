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
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.socket.server.support.DefaultHandshakeHandler

@Configuration
@ConditionalOnWebApplication
open class DgsWebSocketAutoConfig {
    @Bean
    open fun webSocketHandler(@Suppress("SpringJavaInjectionPointsAutowiringInspection") dgsQueryExecutor: DgsQueryExecutor): WebSocketHandler {
        return DgsWebSocketHandler(dgsQueryExecutor)
    }

    @Configuration
    @EnableWebSocket
    internal open class WebSocketConfig(
        @Suppress("SpringJavaInjectionPointsAutowiringInspection") private val webSocketHandler: WebSocketHandler,
        private val handshakeInterceptor: HandshakeInterceptor
    ) : WebSocketConfigurer {

        override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
            val handshakeHandler = DefaultHandshakeHandler()
            registry.addHandler(webSocketHandler, "/subscriptions").setHandshakeHandler(handshakeHandler)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*")
        }
    }

    @Bean
    open fun handshakeInterceptor(): HandshakeInterceptor {
        return DgsHandshakeInterceptor()
    }
}
