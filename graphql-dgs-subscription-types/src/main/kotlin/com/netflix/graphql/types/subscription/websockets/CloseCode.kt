package com.netflix.graphql.types.subscription.websockets

/**
 * `graphql-ws` expected and standard close codes of the [GraphQL over WebSocket Protocol](https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md).
 */
enum class CloseCode(val code: Int) {
    InternalServerError(4500),
    BadRequest(4400),

    /** Tried subscribing before connect ack */
    Unauthorized(4401),
    Forbidden(4403),
    SubprotocolNotAcceptable(4406),
    ConnectionInitialisationTimeout(4408),
    ConnectionAcknowledgementTimeout(4504),

    /** Subscriber distinction is very important */
    SubscriberAlreadyExists(4409),
    TooManyInitialisationRequests(4429);
}
