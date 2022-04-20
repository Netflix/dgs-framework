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
