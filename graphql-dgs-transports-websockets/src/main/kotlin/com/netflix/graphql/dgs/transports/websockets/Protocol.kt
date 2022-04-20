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

/**
 * A websocket sub-protocol for <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL Over WebSocket Protocol</a>
 */
const val GRAPHQL_TRANSPORT_WS_PROTOCOL = "graphql-transport-ws" // graphql-ws subprotocol
/**
 * A websocket sub-protocol for https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 *
 * Note: This protocol is no longer actively maintained, and [GRAPHQL_TRANSPORT_WS_PROTOCOL] should be favored instead.
 */
const val GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL = "graphql-ws" // subscriptions-transport-ws subprotocol
