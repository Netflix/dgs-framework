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
import org.springframework.http.HttpStatusCode;

import java.util.Map;

/**
 * Blocking implementation of a GraphQL client.
 * The user is responsible for doing the actual HTTP request, making this pluggable with any HTTP client.
 * For a more convenient option, use {@link WebClientGraphQLClient} instead.
 *
 * @deprecated Tied to Jackson 2. Migrate to {@link DgsCustomGraphQLClient}, which accepts any
 *             {@code DgsJsonMapper} (defaulting to Jackson 3). This class will be removed in a future release.
 */
@Deprecated
public class CustomGraphQLClient implements GraphQLClient {
    private final String url;
    private final RequestExecutor requestExecutor;
    private final ObjectMapper mapper;

    public CustomGraphQLClient(String url, RequestExecutor requestExecutor, ObjectMapper mapper) {
        this.url = url;
        this.requestExecutor = requestExecutor;
        this.mapper = mapper;
    }

    public CustomGraphQLClient(String url, RequestExecutor requestExecutor) {
        this(url, requestExecutor, GraphQLRequestOptions.createCustomObjectMapper());
    }

    public CustomGraphQLClient(String url, RequestExecutor requestExecutor, GraphQLRequestOptions options) {
        this(url, requestExecutor, GraphQLRequestOptions.createCustomObjectMapper(options));
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

        HttpResponse response = requestExecutor.execute(url, GraphQLClients.defaultHeaders, serializedRequest);

        if (HttpStatusCode.valueOf(response.getStatusCode()).isError()) {
            throw new GraphQLClientException(
                    response.getStatusCode(),
                    url,
                    response.getBody() != null ? response.getBody() : "",
                    serializedRequest);
        }

        return new GraphQLResponse(
                response.getBody() != null ? response.getBody() : "", response.getHeaders(), mapper);
    }
}
