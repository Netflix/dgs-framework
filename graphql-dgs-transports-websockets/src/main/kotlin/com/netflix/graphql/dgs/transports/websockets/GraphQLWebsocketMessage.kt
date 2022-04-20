package com.netflix.graphql.dgs.transports.websockets

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import graphql.ExecutionResult
import graphql.GraphQLError

object MessageType {
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
    JsonSubTypes.Type(GraphQLWebsocketMessage.ConnectionInitMessage::class, name = MessageType.CONNECTION_INIT),
    JsonSubTypes.Type(GraphQLWebsocketMessage.ConnectionAckMessage::class, name = MessageType.CONNECTION_ACK),
    JsonSubTypes.Type(GraphQLWebsocketMessage.PingMessage::class, name = MessageType.PING),
    JsonSubTypes.Type(GraphQLWebsocketMessage.PongMessage::class, name = MessageType.PONG),
    JsonSubTypes.Type(GraphQLWebsocketMessage.SubscribeMessage::class, name = MessageType.SUBSCRIBE),
    JsonSubTypes.Type(GraphQLWebsocketMessage.NextMessage::class, name = MessageType.NEXT),
    JsonSubTypes.Type(GraphQLWebsocketMessage.ErrorMessage::class, name = MessageType.ERROR),
    JsonSubTypes.Type(GraphQLWebsocketMessage.CompleteMessage::class, name = MessageType.COMPLETE),
)
sealed class GraphQLWebsocketMessage(
    @JsonProperty("type")
    val type: String
) {
    data class ConnectionInitMessage(val payload: Map<String, Any>? = null) :
        GraphQLWebsocketMessage(MessageType.CONNECTION_INIT)

    data class ConnectionAckMessage(val payload: Map<String, Any>? = null) :
        GraphQLWebsocketMessage(MessageType.CONNECTION_ACK)

    data class PingMessage(val payload: Map<String, Any>? = null) : GraphQLWebsocketMessage(MessageType.PING)

    data class PongMessage(val payload: Map<String, Any>? = null) : GraphQLWebsocketMessage(MessageType.PONG)

    data class SubscribeMessage(
        val id: String,
        val payload: Payload,
    ) : GraphQLWebsocketMessage(MessageType.SUBSCRIBE) {
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
    ) : GraphQLWebsocketMessage(MessageType.NEXT)

    data class ErrorMessage(
        val id: String,
        val payload: List<GraphQLError>
    ) : GraphQLWebsocketMessage(MessageType.ERROR)

    data class CompleteMessage(
        val id: String
    ) : GraphQLWebsocketMessage(MessageType.COMPLETE)
}
