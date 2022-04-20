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

package com.netflix.graphql.dgs.webmvc.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.internal.CookieValueResolver
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.mvc.DgsRestController
import com.netflix.graphql.dgs.mvc.DgsRestSchemaJsonController
import com.netflix.graphql.dgs.mvc.DgsWebsocketHandler
import com.netflix.graphql.dgs.mvc.ServletCookieValueResolver
import com.netflix.graphql.dgs.transports.websockets.GRAPHQL_TRANSPORT_WS_PROTOCOL
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.support.DefaultHandshakeHandler

@Configuration
@ConditionalOnWebApplication
@EnableConfigurationProperties(DgsWebMvcConfigurationProperties::class)
open class DgsWebMvcAutoConfiguration {
    @Bean
    @Qualifier("dgsObjectMapper")
    @ConditionalOnMissingBean(name = ["dgsObjectMapper"])
    open fun dgsObjectMapper(): ObjectMapper {
        return jacksonObjectMapper()
    }

    @Bean
    open fun dgsRestController(dgsQueryExecutor: DgsQueryExecutor, @Qualifier("dgsObjectMapper") objectMapper: ObjectMapper): DgsRestController {
        return DgsRestController(dgsQueryExecutor, objectMapper)
    }

    @Bean
    open fun servletCookieValueResolver(): CookieValueResolver {
        return ServletCookieValueResolver()
    }

    @Configuration
    @ConditionalOnClass(DispatcherServlet::class)
    @ConditionalOnProperty(name = ["dgs.graphql.graphiql.enabled"], havingValue = "true", matchIfMissing = true)
    @Import(GraphiQLConfigurer::class)
    open class DgsGraphiQLConfiguration

    @Configuration
    @ConditionalOnProperty(name = ["dgs.graphql.schema-json.enabled"], havingValue = "true", matchIfMissing = true)
    open class DgsWebMvcSchemaJsonConfiguration {
        @Bean
        open fun dgsRestSchemaJsonController(dgsSchemaProvider: DgsSchemaProvider): DgsRestSchemaJsonController {
            return DgsRestSchemaJsonController(dgsSchemaProvider)
        }
    }

    @Bean
    @Qualifier("transport-ws")
    open fun transportWebsocketHandler(@Suppress("SpringJavaInjectionPointsAutowiringInspection") dgsQueryExecutor: DgsQueryExecutor): WebSocketHandler {
        return DgsWebsocketHandler(dgsQueryExecutor)
    }

    @Configuration
    @EnableWebSocket
    internal open class WebSocketConfig(@Suppress("SpringJavaInjectionPointsAutowiringInspection") @Qualifier("transport-ws") private val webSocketHandler: WebSocketHandler) :
        WebSocketConfigurer {

        override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
            val defaultHandshakeHandler = DefaultHandshakeHandler()
            defaultHandshakeHandler.setSupportedProtocols(GRAPHQL_TRANSPORT_WS_PROTOCOL)
            registry.addHandler(webSocketHandler, "/graphql")
                .setHandshakeHandler(defaultHandshakeHandler)
                .setAllowedOrigins("*")
        }
    }
}
