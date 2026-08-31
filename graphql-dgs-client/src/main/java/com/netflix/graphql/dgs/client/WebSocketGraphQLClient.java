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
import com.netflix.graphql.types.subscription.OperationMessageType;
import com.netflix.graphql.types.subscription.QueryPayload;
import graphql.GraphQLException;
import org.intellij.lang.annotations.Language;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reactive client implementation using websockets and the subscription-transport-ws protocol:
 * https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 *
 * @deprecated This client is using the deprecated subscription-transport-ws protocol, which is no longer supported by
 *             DGS servers. Use Spring GraphQL WebSocketGraphQlClient instead.
 */
@Deprecated
public class WebSocketGraphQLClient implements ReactiveGraphQLClient {
    private static final Duration DEFAULT_ACKNOWLEDGEMENT_TIMEOUT = Duration.ofSeconds(30);
    private static final OperationMessage CONNECTION_INIT_MESSAGE =
            new OperationMessage(OperationMessageType.GQL_CONNECTION_INIT, null, null);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OperationMessageWebSocketClient client;
    private final Duration acknowledgementTimeout;

    private final AtomicLong subscriptionCount = new AtomicLong(0L);

    // The handshake represents a connection to the server, it is cached so that there is one per client instance.
    // The handshake only completes once the connection has been established and a GQL_CONNECTION_ACK message has been
    // received from the server. If the connection closes it is reestablished and the handshake is reperformed on the
    // next downstream subscribe.
    private final AtomicReference<Disposable> connection = new AtomicReference<>(null);
    private final Mono<Void> handshake;

    public WebSocketGraphQLClient(OperationMessageWebSocketClient client, Duration acknowledgementTimeout) {
        this.client = client;
        this.acknowledgementTimeout = acknowledgementTimeout;
        this.handshake = Mono.defer(() -> {
            if (connectionIsStale()) {
                return doHandshake();
            }
            return Mono.empty();
        });
    }

    public WebSocketGraphQLClient(String url, WebSocketClient client, Duration acknowledgementTimeout) {
        this(new OperationMessageWebSocketClient(url, client), acknowledgementTimeout);
    }

    public WebSocketGraphQLClient(String url, WebSocketClient client) {
        this(new OperationMessageWebSocketClient(url, client), DEFAULT_ACKNOWLEDGEMENT_TIMEOUT);
    }

    public WebSocketGraphQLClient(String url) {
        this(new OperationMessageWebSocketClient(url, new ReactorNettyWebSocketClient()),
                DEFAULT_ACKNOWLEDGEMENT_TIMEOUT);
    }

    public WebSocketGraphQLClient(OperationMessageWebSocketClient client) {
        this(client, DEFAULT_ACKNOWLEDGEMENT_TIMEOUT);
    }

    @Override
    public Flux<GraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query, Map<String, Object> variables) {
        return reactiveExecuteQuery(query, variables, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Flux<GraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query, Map<String, Object> variables, String operationName) {
        // Generate a unique number for each subscription in the same session.
        String subscriptionId = String.valueOf(subscriptionCount.incrementAndGet());
        OperationMessage queryMessage =
                new OperationMessage(
                        OperationMessageType.GQL_START,
                        new QueryPayload(variables, Map.of(), operationName, query),
                        subscriptionId);
        OperationMessage stopMessage = new OperationMessage(OperationMessageType.GQL_STOP, null, subscriptionId);

        // Because handshake is cached it should have only been done once, all subsequent calls to
        // reactiveExecuteQuery() will proceed straight to client.receive()
        return handshake
                .then(Mono.fromRunnable(() -> client.send(queryMessage)))
                .thenMany(client
                        .receive()
                        .filter(message -> subscriptionId.equals(message.getId()))
                        .takeUntil(message -> OperationMessageType.GQL_COMPLETE.equals(message.getType()))
                        .doOnCancel(() -> client.send(stopMessage))
                        .flatMap(this::handleMessage));
    }

    private boolean connectionIsStale() {
        Disposable current = connection.get();
        return current == null || current.isDisposed();
    }

    private Mono<Void> doHandshake() {
        return Mono.defer(() -> {
            connection.set(client.connect().subscribe());

            client.send(CONNECTION_INIT_MESSAGE);
            return client
                    .receive()
                    .take(1)
                    .map(message -> {
                        if (OperationMessageType.GQL_CONNECTION_ACK.equals(message.getType())) {
                            return message;
                        }
                        throw new GraphQLException("Acknowledgement expected from server, received " + message);
                    })
                    .timeout(acknowledgementTimeout)
                    .then();
        });
    }

    private Flux<GraphQLResponse> handleMessage(OperationMessage message) {
        String type = message.getType();
        // Do nothing if no data provided
        if (OperationMessageType.GQL_CONNECTION_ACK.equals(type)
                || OperationMessageType.GQL_CONNECTION_KEEP_ALIVE.equals(type)
                || OperationMessageType.GQL_COMPLETE.equals(type)) {
            return Flux.empty();
        }
        // Convert data to GraphQLResponse
        if (OperationMessageType.GQL_DATA.equals(type)) {
            Object payload = message.getPayload();
            try {
                return Flux.just(new GraphQLResponse(MAPPER.writeValueAsString(payload)));
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }
        // Convert errors received from the server into exceptions, does not include GraphQL execution errors which
        // are bundled in the GraphQLResponse above.
        if (OperationMessageType.GQL_CONNECTION_ERROR.equals(type) || OperationMessageType.GQL_ERROR.equals(type)) {
            throw new GraphQLException(String.valueOf(message.getPayload()));
        }
        // The message is invalid according to the subscriptions transport protocol so it should result in an exception
        throw new GraphQLException("Unable to handle message of type " + type + ". Full message: " + message);
    }
}
