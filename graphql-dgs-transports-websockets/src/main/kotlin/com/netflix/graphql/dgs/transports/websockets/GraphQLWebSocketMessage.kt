package com.netflix.graphql.dgs.transports.websockets

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import graphql.ExecutionResult
import graphql.GraphQLError

object GraphQLWebsocketMessageType {
    // Message types
    const val CONNECTION_INIT = "connection_init"
    const val CONNECTION_ACK = "connection_ack"
    const val PING = "ping"
    const val PONG = "pong"
    const val SUBSCRIBE = "subscribe"
    const val NEXT = "next"
    const val ERROR = "error"
    const val COMPLETE = "complete"
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(GraphQLWebsocketMessage.ConnectionInitMessage::class, name = GraphQLWebsocketMessageType.CONNECTION_INIT),
    JsonSubTypes.Type(GraphQLWebsocketMessage.ConnectionAckMessage::class, name = GraphQLWebsocketMessageType.CONNECTION_ACK),
    JsonSubTypes.Type(GraphQLWebsocketMessage.PingMessage::class, name = GraphQLWebsocketMessageType.PING),
    JsonSubTypes.Type(GraphQLWebsocketMessage.PongMessage::class, name = GraphQLWebsocketMessageType.PONG),
    JsonSubTypes.Type(GraphQLWebsocketMessage.SubscribeMessage::class, name = GraphQLWebsocketMessageType.SUBSCRIBE),
    JsonSubTypes.Type(GraphQLWebsocketMessage.NextMessage::class, name = GraphQLWebsocketMessageType.NEXT),
    JsonSubTypes.Type(GraphQLWebsocketMessage.ErrorMessage::class, name = GraphQLWebsocketMessageType.ERROR),
    JsonSubTypes.Type(GraphQLWebsocketMessage.CompleteMessage::class, name = GraphQLWebsocketMessageType.COMPLETE),
)
sealed class GraphQLWebsocketMessage(
    @JsonProperty("type")
    val type: String
) {
    data class ConnectionInitMessage(val payload: Map<String, Any>? = null) :
        GraphQLWebsocketMessage(GraphQLWebsocketMessageType.CONNECTION_INIT)

    data class ConnectionAckMessage(val payload: Map<String, Any>? = null) :
        GraphQLWebsocketMessage(GraphQLWebsocketMessageType.CONNECTION_ACK)

    data class PingMessage(val payload: Map<String, Any>? = null) : GraphQLWebsocketMessage(GraphQLWebsocketMessageType.PING)

    data class PongMessage(val payload: Map<String, Any>? = null) : GraphQLWebsocketMessage(GraphQLWebsocketMessageType.PONG)

    data class SubscribeMessage(
        val id: String,
        val payload: Payload,
    ) : GraphQLWebsocketMessage(GraphQLWebsocketMessageType.SUBSCRIBE) {
        data class Payload(
            val operationName: String? = null,
            val query: String,
            val variables: Map<String, Any>? = null,
            val extensions: Map<String, Any>? = null,
        )
    }

    data class NextMessage(
        val id: String,
        val payload: ExecutionResult,
    ) : GraphQLWebsocketMessage(GraphQLWebsocketMessageType.NEXT)

    data class ErrorMessage(
        val id: String,
        val payload: List<GraphQLError>
    ) : GraphQLWebsocketMessage(GraphQLWebsocketMessageType.ERROR)

    data class CompleteMessage(
        val id: String
    ) : GraphQLWebsocketMessage(GraphQLWebsocketMessageType.COMPLETE)
}
