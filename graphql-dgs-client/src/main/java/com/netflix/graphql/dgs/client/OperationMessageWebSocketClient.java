/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.graphql.dgs.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.types.subscription.OperationMessage;
import com.netflix.graphql.types.subscription.Protocol;
import graphql.GraphQLException;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;

/** Wrapper around a WebSocketClient for sending/receiving OperationMessages. */
public class OperationMessageWebSocketClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String url;
    private final WebSocketClient client;

    // Sinks are used as buffers, incoming messages from the server are buffered in incomingSink before being
    // consumed. Outgoing messages for the server are buffered in outgoingSink before being sent.
    // The false flag prevents the sink from auto-cancelling on the completion of a single subscriber.
    private final Sinks.Many<OperationMessage> incomingSink =
            Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
    private final Sinks.Many<OperationMessage> outgoingSink =
            Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
    private final Sinks.Many<GraphQLException> errorSink =
            Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

    public OperationMessageWebSocketClient(String url, WebSocketClient client) {
        this.url = url;
        this.client = client;
    }

    public Mono<Void> connect() {
        return Mono.defer(() -> client.execute(
                URI.create(url),
                new WebSocketHandler() {
                    @Override
                    public Mono<Void> handle(WebSocketSession session) {
                        return exchange(session);
                    }

                    @Override
                    public List<String> getSubProtocols() {
                        return List.of(Protocol.GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL);
                    }
                }));
    }

    /**
     * Send a message to the server, the message is buffered for sending later if connection has not been established.
     *
     * @param message The OperationMessage to send
     */
    public void send(OperationMessage message) {
        outgoingSink.tryEmitNext(message).orThrow();
    }

    /**
     * Stream messages from the server, lazily establish connection.
     *
     * @return Flux of OperationMessages
     */
    public Flux<OperationMessage> receive() {
        return incomingSink.asFlux().mergeWith(errorSink.asFlux().map(error -> {
            throw error;
        }));
    }

    private Mono<Void> exchange(WebSocketSession session) {
        // Create chains to handle de/serialization
        Flux<OperationMessage> incomingDeserialized =
                session.receive().map(this::decodeMessage).doOnNext(incomingSink::tryEmitNext);
        Mono<Void> outgoingSerialized =
                session.send(outgoingSink.asFlux().map(message -> createMessage(session, message)));

        // Transfer the contents of the sinks to/from the server
        return Flux
                .merge(incomingDeserialized, outgoingSerialized)
                .then()
                // Ensure the output flux collapses neatly if an error occurs
                .doOnError(error -> errorSink.tryEmitNext(new GraphQLException(error)).orThrow())
                .doAfterTerminate(() -> errorSink
                        .tryEmitNext(new GraphQLException("Server closed the connection unexpectedly"))
                        .orThrow());
    }

    private WebSocketMessage createMessage(WebSocketSession session, OperationMessage message) {
        try {
            return session.textMessage(MAPPER.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private OperationMessage decodeMessage(WebSocketMessage message) {
        try {
            return MAPPER.readValue(message.getPayloadAsText(), OperationMessage.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
