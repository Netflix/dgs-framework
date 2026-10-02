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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.function.Consumer;

/**
 * A RestClient implementation of the DGS Client for blocking use.
 * A RestClient instance configured for the graphql endpoint (at least an url) must be provided.
 *
 * @deprecated Tied to Jackson 2. Migrate to {@link DgsRestClientGraphQLClient}, which accepts any
 *             {@code DgsJsonMapper} (defaulting to Jackson 3). This class will be removed in a future release.
 */
@Deprecated
public class RestClientGraphQLClient implements GraphQLClient {
    private final RestClient restClient;
    private final Consumer<HttpHeaders> headersConsumer;
    private final ObjectMapper mapper;

    public RestClientGraphQLClient(
            @NotNull RestClient restClient, @NotNull Consumer<HttpHeaders> headersConsumer, @NotNull ObjectMapper mapper) {
        this.restClient = restClient;
        this.headersConsumer = headersConsumer;
        this.mapper = mapper;
    }

    public RestClientGraphQLClient(@NotNull RestClient restClient) {
        this(restClient, headers -> { });
    }

    public RestClientGraphQLClient(@NotNull RestClient restClient, @NotNull ObjectMapper mapper) {
        this(restClient, headers -> { }, mapper);
    }

    public RestClientGraphQLClient(@NotNull RestClient restClient, @NotNull Consumer<HttpHeaders> headersConsumer) {
        this(restClient, headersConsumer, GraphQLRequestOptions.createCustomObjectMapper());
    }

    public RestClientGraphQLClient(@NotNull RestClient restClient, @Nullable GraphQLRequestOptions options) {
        this(restClient, headers -> { }, GraphQLRequestOptions.createCustomObjectMapper(options));
    }

    @NotNull
    @Override
    public GraphQLResponse executeQuery(@NotNull @Language("graphql") String query) {
        return executeQuery(query, Map.of(), (String) null);
    }

    @NotNull
    @Override
    public GraphQLResponse executeQuery(@NotNull @Language("graphql") String query, @NotNull Map<String, ? extends Object> variables) {
        return executeQuery(query, variables, (String) null);
    }

    @NotNull
    @Override
    public GraphQLResponse executeQuery(
            @NotNull @Language("graphql") String query, @NotNull Map<String, ? extends Object> variables, @Nullable String operationName) {
        String serializedRequest = ClientRequests.serialize(mapper, query, operationName, variables);

        ResponseEntity<String> responseEntity =
                restClient
                        .post()
                        .headers(headers -> GraphQLClients.defaultHeaders.forEach(headers::addAll))
                        .headers(this.headersConsumer)
                        .body(serializedRequest)
                        .retrieve()
                        .toEntity(String.class);

        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
            throw new GraphQLClientException(
                    responseEntity.getStatusCode().value(),
                    "",
                    responseEntity.getBody() != null ? responseEntity.getBody() : "",
                    serializedRequest);
        }

        return new GraphQLResponse(
                responseEntity.getBody() != null ? responseEntity.getBody() : "",
                HttpHeaderUtils.toMap(responseEntity.getHeaders()),
                mapper);
    }
}
