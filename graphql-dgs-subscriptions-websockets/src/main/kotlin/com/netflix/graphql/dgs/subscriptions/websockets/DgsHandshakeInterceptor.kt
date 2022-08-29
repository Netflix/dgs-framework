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

package com.netflix.graphql.dgs.subscriptions.websockets

import com.netflix.graphql.types.subscription.GRAPHQL_SUBSCRIPTIONS_TRANSPORT_WS_PROTOCOL
import com.netflix.graphql.types.subscription.GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL
import org.slf4j.LoggerFactory
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor
import java.lang.Exception

class DgsHandshakeInterceptor : HttpSessionHandshakeInterceptor() {
    private val logger = LoggerFactory.getLogger(DgsHandshakeInterceptor::class.java)
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        if (request.headers[WebSocketHttpHeaders.SEC_WEBSOCKET_PROTOCOL].isNullOrEmpty()) {
            request.headers.set(WebSocketHttpHeaders.SEC_WEBSOCKET_PROTOCOL, GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL)
            request.headers.set(WebSocketHttpHeaders.SEC_WEBSOCKET_PROTOCOL, GRAPHQL_SUBSCRIPTIONS_TRANSPORT_WS_PROTOCOL)
        }
        return super.beforeHandshake(request, response, wsHandler, attributes)
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) {
    }
}
