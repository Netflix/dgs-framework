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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.intellij.lang.annotations.Language;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Consumer;

/**
 * A WebClient implementation of the DGS Client.
 * A WebClient instance configured for the graphql endpoint (at least an url) must be provided.
 *
 * @deprecated Tied to Jackson 2. Migrate to {@link DgsWebClientGraphQLClient}, which accepts any
 *             {@code DgsJsonMapper} (defaulting to Jackson 3). This class will be removed in a future release.
 */
@Deprecated
public class WebClientGraphQLClient implements MonoGraphQLClient {
    private static final RequestBodyUriCustomizer REQUEST_BODY_URI_CUSTOMIZER_IDENTITY = spec -> spec;

    private final WebClient webclient;
    private final Consumer<HttpHeaders> headersConsumer;
    private final ObjectMapper mapper;

    public WebClientGraphQLClient(
            WebClient webclient, Consumer<HttpHeaders> headersConsumer, ObjectMapper mapper) {
        this.webclient = webclient;
        this.headersConsumer = headersConsumer;
        this.mapper = mapper;
    }

    public WebClientGraphQLClient(WebClient webclient) {
        this(webclient, headers -> { });
    }

    public WebClientGraphQLClient(WebClient webclient, Consumer<HttpHeaders> headersConsumer) {
        this(webclient, headersConsumer, GraphQLRequestOptions.createCustomObjectMapper());
    }

    public WebClientGraphQLClient(WebClient webclient, GraphQLRequestOptions options) {
        this(webclient, headers -> { }, GraphQLRequestOptions.createCustomObjectMapper(options));
    }

    public WebClientGraphQLClient(WebClient webclient, ObjectMapper mapper) {
        this(webclient, headers -> { }, mapper);
    }

    public WebClientGraphQLClient(
            WebClient webclient, Consumer<HttpHeaders> headersConsumer, GraphQLRequestOptions options) {
        this(webclient, headersConsumer, GraphQLRequestOptions.createCustomObjectMapper(options));
    }

    @Override
    public Mono<GraphQLResponse> reactiveExecuteQuery(@Language("graphql") String query) {
        return reactiveExecuteQuery(query, Map.of(), (String) null);
    }

    @Override
    public Mono<GraphQLResponse> reactiveExecuteQuery(@Language("graphql") String query, Map<String, Object> variables) {
        return reactiveExecuteQuery(query, variables, (String) null);
    }

    @Override
    public Mono<GraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query, Map<String, Object> variables, String operationName) {
        return reactiveExecuteQuery(query, variables, operationName, REQUEST_BODY_URI_CUSTOMIZER_IDENTITY);
    }

    /**
     * @param requestBodyUriCustomizer Allows customization of the URI and headers. This occurs before both the
     *                                 headers consumer and serialization of the GraphQL request to the body occurs.
     *                                 In other words, the headers consumer will take precedence.
     */
    public Mono<GraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query, RequestBodyUriCustomizer requestBodyUriCustomizer) {
        return reactiveExecuteQuery(query, Map.of(), null, requestBodyUriCustomizer);
    }

    /**
     * @param requestBodyUriCustomizer Allows customization of the URI and headers. This occurs before both the
     *                                 headers consumer and serialization of the GraphQL request to the body occurs.
     *                                 In other words, the headers consumer will take precedence.
     */
    public Mono<GraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query,
            Map<String, Object> variables,
            String operationName,
            RequestBodyUriCustomizer requestBodyUriCustomizer) {
        String serializedRequest = ClientRequests.serialize(mapper, query, operationName, variables);

        return requestBodyUriCustomizer
                .apply(webclient.post())
                .headers(headers -> GraphQLClients.defaultHeaders.forEach(headers::addAll))
                .headers(this.headersConsumer)
                .bodyValue(serializedRequest)
                .retrieve()
                .toEntity(String.class)
                .map(httpResponse -> handleResponse(httpResponse, serializedRequest));
    }

    private GraphQLResponse handleResponse(ResponseEntity<String> response, String requestBody) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new GraphQLClientException(
                    response.getStatusCode().value(),
                    webclient.toString(),
                    response.getBody() != null ? response.getBody() : "",
                    requestBody);
        }

        return new GraphQLResponse(
                response.getBody() != null ? response.getBody() : "",
                HttpHeaderUtils.toMap(response.getHeaders()),
                mapper);
    }

    /**
     * Allows customization of the request URI and headers of {@link WebClientGraphQLClient}, returning the
     * modified {@link RequestBodySpec}. This could be used to set URI query parameters, for example.
     */
    @FunctionalInterface
    public interface RequestBodyUriCustomizer {
        RequestBodySpec apply(WebClient.RequestBodyUriSpec spec);
    }
}
