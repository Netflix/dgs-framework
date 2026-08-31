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
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Non-blocking implementation of a GraphQL client, based on the {@link Mono} type.
 * The user is responsible for doing the actual HTTP request, making this pluggable with any HTTP client.
 * For a more convenient option, use {@link WebClientGraphQLClient} instead.
 *
 * @deprecated Tied to Jackson 2. Migrate to {@link DgsCustomMonoGraphQLClient}, which accepts any
 *             {@code DgsJsonMapper} (defaulting to Jackson 3). This class will be removed in a future release.
 */
@Deprecated
public class CustomMonoGraphQLClient implements MonoGraphQLClient {
    private final String url;
    private final MonoRequestExecutor monoRequestExecutor;
    private final ObjectMapper mapper;

    public CustomMonoGraphQLClient(String url, MonoRequestExecutor monoRequestExecutor, ObjectMapper mapper) {
        this.url = url;
        this.monoRequestExecutor = monoRequestExecutor;
        this.mapper = mapper;
    }

    public CustomMonoGraphQLClient(String url, MonoRequestExecutor monoRequestExecutor) {
        this(url, monoRequestExecutor, GraphQLRequestOptions.createCustomObjectMapper());
    }

    public CustomMonoGraphQLClient(
            String url, MonoRequestExecutor monoRequestExecutor, GraphQLRequestOptions options) {
        this(url, monoRequestExecutor, GraphQLRequestOptions.createCustomObjectMapper(options));
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
        String serializedRequest = ClientRequests.serialize(mapper, query, operationName, variables);
        return monoRequestExecutor
                .execute(url, GraphQLClients.defaultHeaders, serializedRequest)
                .map(response -> {
                    if (HttpStatusCode.valueOf(response.getStatusCode()).isError()) {
                        throw new GraphQLClientException(
                                response.getStatusCode(),
                                url,
                                response.getBody() != null ? response.getBody() : "",
                                serializedRequest);
                    }
                    return new GraphQLResponse(
                            response.getBody() != null ? response.getBody() : "", response.getHeaders(), mapper);
                });
    }
}
