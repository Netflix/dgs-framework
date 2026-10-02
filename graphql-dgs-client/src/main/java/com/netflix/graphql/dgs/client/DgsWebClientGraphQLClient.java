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

import com.netflix.graphql.dgs.json.DgsJsonMapper;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Consumer;

/**
 * WebClient-based reactive DGS client.
 * The no-arg convenience constructor uses Jackson 3 under the hood. Callers on Jackson 2
 * must pass {@link Jackson2DgsJsonMapperAdapter} explicitly.
 */
public class DgsWebClientGraphQLClient implements DgsMonoGraphQLClient {
    private static final RequestBodyUriCustomizer REQUEST_BODY_URI_CUSTOMIZER_IDENTITY = spec -> spec;

    private final WebClient webclient;
    private final Consumer<HttpHeaders> headersConsumer;
    private final DgsJsonMapper mapper;

    public DgsWebClientGraphQLClient(
            @NotNull WebClient webclient, @NotNull Consumer<HttpHeaders> headersConsumer, @NotNull DgsJsonMapper mapper) {
        this.webclient = webclient;
        this.headersConsumer = headersConsumer;
        this.mapper = mapper;
    }

    public DgsWebClientGraphQLClient(@NotNull WebClient webclient) {
        this(webclient, headers -> { });
    }

    public DgsWebClientGraphQLClient(@NotNull WebClient webclient, @NotNull Consumer<HttpHeaders> headersConsumer) {
        this(webclient, headersConsumer, Jackson3DgsJsonMapperAdapter.defaultMapper());
    }

    public DgsWebClientGraphQLClient(@NotNull WebClient webclient, @NotNull DgsGraphQLRequestOptions options) {
        this(webclient, headers -> { }, Jackson3DgsJsonMapperAdapter.fromOptions(options));
    }

    public DgsWebClientGraphQLClient(
            @NotNull WebClient webclient, @NotNull Consumer<HttpHeaders> headersConsumer, @NotNull DgsGraphQLRequestOptions options) {
        this(webclient, headersConsumer, Jackson3DgsJsonMapperAdapter.fromOptions(options));
    }

    @NotNull
    @Override
    public Mono<DgsGraphQLResponse> reactiveExecuteQuery(@NotNull @Language("graphql") String query) {
        return reactiveExecuteQuery(query, Map.of(), null);
    }

    @NotNull
    @Override
    public Mono<DgsGraphQLResponse> reactiveExecuteQuery(
            @NotNull @Language("graphql") String query, @NotNull Map<String, ? extends Object> variables) {
        return reactiveExecuteQuery(query, variables, null);
    }

    @NotNull
    @Override
    public Mono<DgsGraphQLResponse> reactiveExecuteQuery(
            @NotNull @Language("graphql") String query, @NotNull Map<String, ? extends Object> variables, @Nullable String operationName) {
        return reactiveExecuteQuery(query, variables, operationName, REQUEST_BODY_URI_CUSTOMIZER_IDENTITY);
    }

    @NotNull
    public Mono<DgsGraphQLResponse> reactiveExecuteQuery(
            @NotNull @Language("graphql") String query, @NotNull RequestBodyUriCustomizer requestBodyUriCustomizer) {
        return reactiveExecuteQuery(query, Map.of(), null, requestBodyUriCustomizer);
    }

    @NotNull
    public Mono<DgsGraphQLResponse> reactiveExecuteQuery(
            @NotNull @Language("graphql") String query,
            @NotNull Map<String, ? extends Object> variables,
            @Nullable String operationName,
            @NotNull RequestBodyUriCustomizer requestBodyUriCustomizer) {
        String serializedRequest = mapper.writeValueAsString(GraphQLClients.toRequestMap(query, operationName, variables));

        return requestBodyUriCustomizer
                .apply(webclient.post())
                .headers(headers -> GraphQLClients.defaultHeaders.forEach(headers::addAll))
                .headers(this.headersConsumer)
                .bodyValue(serializedRequest)
                .retrieve()
                .toEntity(String.class)
                .map(httpResponse -> handleResponse(httpResponse, serializedRequest));
    }

    private DgsGraphQLResponse handleResponse(ResponseEntity<String> response, String requestBody) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new GraphQLClientException(
                    response.getStatusCode().value(),
                    webclient.toString(),
                    response.getBody() != null ? response.getBody() : "",
                    requestBody);
        }
        return new DefaultDgsGraphQLResponse(
                response.getBody() != null ? response.getBody() : "",
                HttpHeaderUtils.toMap(response.getHeaders()),
                mapper);
    }

    @FunctionalInterface
    public interface RequestBodyUriCustomizer {
        @NotNull
        RequestBodySpec apply(@NotNull WebClient.RequestBodyUriSpec spec);
    }
}
