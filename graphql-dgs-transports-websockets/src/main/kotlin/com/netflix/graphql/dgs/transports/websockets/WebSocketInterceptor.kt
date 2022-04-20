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

package com.netflix.graphql.dgs.transports.websockets

interface WebSocketInterceptor {
    /**
     * Handle the payload from the connection initialization message that a
     * GraphQL over WebSocket client must send after the WebSocket session is
     * established and before sending any requests.
     * @param payload the payload from the {@code ConnectionInitMessage}
     * @return an optional payload for the {@code ConnectionAckMessage}
     */
    fun connectionInitialization(payload: Map<String, Any>? = null): Any? {
        return null
    }

    /**
     * Handle the completion message that a GraphQL over WebSocket clients sends
     * before closing the WebSocket connection.
     */
    fun connectionCompletion() {
    }
    /**
     * Implement a listener for the [GraphQLWebsocketMessage.PongMessage]` sent from the client to the server.
     * If the client sent the pong with a payload, it will be passed through the
     * first argument.
     */
    fun pong(payload: Map<String, Any>? = null) {
    }
    /**
     * Implement a listener for the [GraphQLWebsocketMessage.PingMessage] sent from the client to the server.
     * If the client sent the ping with a payload, it will be passed through the
     * first argument.
     *
     * If this listener is implemented, the server will NOT automatically reply
     * to any pings from the client. Implementing it makes it your responsibility
     * to decide how and when to respond.
     */
    fun ping(payload: Map<String, Any>? = null) {
    }
}
