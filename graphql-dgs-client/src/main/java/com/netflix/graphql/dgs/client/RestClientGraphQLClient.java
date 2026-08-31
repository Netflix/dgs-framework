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
            RestClient restClient, Consumer<HttpHeaders> headersConsumer, ObjectMapper mapper) {
        this.restClient = restClient;
        this.headersConsumer = headersConsumer;
        this.mapper = mapper;
    }

    public RestClientGraphQLClient(RestClient restClient) {
        this(restClient, headers -> { });
    }

    public RestClientGraphQLClient(RestClient restClient, ObjectMapper mapper) {
        this(restClient, headers -> { }, mapper);
    }

    public RestClientGraphQLClient(RestClient restClient, Consumer<HttpHeaders> headersConsumer) {
        this(restClient, headersConsumer, GraphQLRequestOptions.createCustomObjectMapper());
    }

    public RestClientGraphQLClient(RestClient restClient, GraphQLRequestOptions options) {
        this(restClient, headers -> { }, GraphQLRequestOptions.createCustomObjectMapper(options));
    }

    @Override
    public GraphQLResponse executeQuery(@Language("graphql") String query) {
        return executeQuery(query, Map.of(), (String) null);
    }

    @Override
    public GraphQLResponse executeQuery(@Language("graphql") String query, Map<String, Object> variables) {
        return executeQuery(query, variables, (String) null);
    }

    @Override
    public GraphQLResponse executeQuery(
            @Language("graphql") String query, Map<String, Object> variables, String operationName) {
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
