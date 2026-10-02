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
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.function.Consumer;

/**
 * RestClient-based blocking DGS client.
 * The no-arg convenience constructor uses Jackson 3 under the hood. Callers on Jackson 2
 * must pass {@link Jackson2DgsJsonMapperAdapter} explicitly.
 */
public class DgsRestClientGraphQLClient implements DgsGraphQLClient {
    private final RestClient restClient;
    private final Consumer<HttpHeaders> headersConsumer;
    private final DgsJsonMapper mapper;

    public DgsRestClientGraphQLClient(
            @NotNull RestClient restClient, @NotNull Consumer<HttpHeaders> headersConsumer, @NotNull DgsJsonMapper mapper) {
        this.restClient = restClient;
        this.headersConsumer = headersConsumer;
        this.mapper = mapper;
    }

    public DgsRestClientGraphQLClient(@NotNull RestClient restClient) {
        this(restClient, headers -> { });
    }

    public DgsRestClientGraphQLClient(@NotNull RestClient restClient, @NotNull Consumer<HttpHeaders> headersConsumer) {
        this(restClient, headersConsumer, Jackson3DgsJsonMapperAdapter.defaultMapper());
    }

    public DgsRestClientGraphQLClient(@NotNull RestClient restClient, @NotNull DgsGraphQLRequestOptions options) {
        this(restClient, headers -> { }, Jackson3DgsJsonMapperAdapter.fromOptions(options));
    }

    @NotNull
    @Override
    public DgsGraphQLResponse executeQuery(@NotNull @Language("graphql") String query) {
        return executeQuery(query, Map.of(), null);
    }

    @NotNull
    @Override
    public DgsGraphQLResponse executeQuery(@NotNull @Language("graphql") String query, @NotNull Map<String, ? extends Object> variables) {
        return executeQuery(query, variables, null);
    }

    @NotNull
    @Override
    public DgsGraphQLResponse executeQuery(
            @NotNull @Language("graphql") String query, @NotNull Map<String, ? extends Object> variables, @Nullable String operationName) {
        String serializedRequest = mapper.writeValueAsString(GraphQLClients.toRequestMap(query, operationName, variables));

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

        return new DefaultDgsGraphQLResponse(
                responseEntity.getBody() != null ? responseEntity.getBody() : "",
                HttpHeaderUtils.toMap(responseEntity.getHeaders()),
                mapper);
    }
}
