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

import org.slf4j.LoggerFactory
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.server.RequestUpgradeStrategy
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

class DgsHandshakeWebSocketService : HandshakeWebSocketService {

    constructor() : super()

    constructor (upgradeStrategy: RequestUpgradeStrategy) : super(upgradeStrategy)

    override fun handleRequest(exchange: ServerWebExchange, handler: WebSocketHandler): Mono<Void> {
        var newExchange = exchange
        var request = exchange.request
        val headers = request.headers
        val protocols = headers[SEC_WEBSOCKET_PROTOCOL]

        if (protocols.isNullOrEmpty()) {
            request = request.mutate().header(SEC_WEBSOCKET_PROTOCOL, "graphql-ws").build()
            newExchange = newExchange.mutate().request(request).build()
        }

        return super.handleRequest(newExchange, handler)
    }

    companion object {
        private const val SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol"
        private val logger = LoggerFactory.getLogger(DgsHandshakeWebSocketService::class.java)
    }
}
