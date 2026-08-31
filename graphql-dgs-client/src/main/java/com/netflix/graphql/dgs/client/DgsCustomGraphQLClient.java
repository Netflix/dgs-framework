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
import org.springframework.http.HttpStatusCode;

import java.util.Map;

/**
 * Blocking GraphQL client that delegates the HTTP call to a user-supplied {@link RequestExecutor}.
 * The no-arg convenience constructor uses Jackson 3 under the hood. Callers on Jackson 2
 * must pass {@link Jackson2DgsJsonMapperAdapter} explicitly.
 */
public class DgsCustomGraphQLClient implements DgsGraphQLClient {
    private final String url;
    private final RequestExecutor requestExecutor;
    private final DgsJsonMapper mapper;

    public DgsCustomGraphQLClient(String url, RequestExecutor requestExecutor, DgsJsonMapper mapper) {
        this.url = url;
        this.requestExecutor = requestExecutor;
        this.mapper = mapper;
    }

    public DgsCustomGraphQLClient(String url, RequestExecutor requestExecutor) {
        this(url, requestExecutor, Jackson3DgsJsonMapperAdapter.defaultMapper());
    }

    public DgsCustomGraphQLClient(String url, RequestExecutor requestExecutor, DgsGraphQLRequestOptions options) {
        this(url, requestExecutor, Jackson3DgsJsonMapperAdapter.fromOptions(options));
    }

    @Override
    public DgsGraphQLResponse executeQuery(@Language("graphql") String query) {
        return executeQuery(query, Map.of(), null);
    }

    @Override
    public DgsGraphQLResponse executeQuery(@Language("graphql") String query, Map<String, Object> variables) {
        return executeQuery(query, variables, null);
    }

    @Override
    public DgsGraphQLResponse executeQuery(
            @Language("graphql") String query, Map<String, Object> variables, String operationName) {
        String serializedRequest = mapper.writeValueAsString(GraphQLClients.toRequestMap(query, operationName, variables));

        HttpResponse response = requestExecutor.execute(url, GraphQLClients.defaultHeaders, serializedRequest);
        if (HttpStatusCode.valueOf(response.getStatusCode()).isError()) {
            throw new GraphQLClientException(
                    response.getStatusCode(),
                    url,
                    response.getBody() != null ? response.getBody() : "",
                    serializedRequest);
        }
        return new DefaultDgsGraphQLResponse(
                response.getBody() != null ? response.getBody() : "", response.getHeaders(), mapper);
    }
}
