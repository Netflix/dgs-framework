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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Consumer;

/**
 * GraphQL client interface for reactive clients.
 *
 * @deprecated Tied to Jackson 2 through {@link GraphQLResponse}. Program against {@link DgsMonoGraphQLClient}
 *             instead. This interface will be removed in a future release.
 */
@Deprecated
public interface MonoGraphQLClient extends DgsMonoGraphQLClient {
    /**
     * A reactive call to execute a query and parse its result.
     * Don't forget to subscribe() to actually send the query!
     */
    @Override
    Mono<GraphQLResponse> reactiveExecuteQuery(@Language("graphql") String query);

    @Override
    Mono<GraphQLResponse> reactiveExecuteQuery(@Language("graphql") String query, Map<String, Object> variables);

    @Override
    Mono<GraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query, Map<String, Object> variables, String operationName);

    /**
     * @deprecated The RequestExecutor should be provided while creating the implementation.
     *             Use CustomGraphQLClient/CustomMonoGraphQLClient instead.
     */
    @Deprecated
    default Mono<GraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query, Map<String, Object> variables, MonoRequestExecutor requestExecutor) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated The RequestExecutor should be provided while creating the implementation.
     *             Use CustomGraphQLClient/CustomMonoGraphQLClient instead.
     */
    @Deprecated
    default Mono<GraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query,
            Map<String, Object> variables,
            String operationName,
            MonoRequestExecutor requestExecutor) {
        throw new UnsupportedOperationException();
    }

    static CustomMonoGraphQLClient createCustomReactive(
            @Language("url") String url, MonoRequestExecutor requestExecutor) {
        return new CustomMonoGraphQLClient(url, requestExecutor);
    }

    static CustomMonoGraphQLClient createCustomReactive(
            @Language("url") String url, MonoRequestExecutor requestExecutor, GraphQLRequestOptions options) {
        return new CustomMonoGraphQLClient(url, requestExecutor, options);
    }

    static WebClientGraphQLClient createWithWebClient(WebClient webClient) {
        return new WebClientGraphQLClient(webClient);
    }

    static WebClientGraphQLClient createWithWebClient(WebClient webClient, ObjectMapper objectMapper) {
        return new WebClientGraphQLClient(webClient, objectMapper);
    }

    static WebClientGraphQLClient createWithWebClient(WebClient webClient, Consumer<HttpHeaders> headersConsumer) {
        return new WebClientGraphQLClient(webClient, headersConsumer);
    }

    static WebClientGraphQLClient createWithWebClient(WebClient webClient, GraphQLRequestOptions options) {
        return new WebClientGraphQLClient(webClient, headers -> { }, options);
    }

    static WebClientGraphQLClient createWithWebClient(
            WebClient webClient, Consumer<HttpHeaders> headersConsumer, GraphQLRequestOptions options) {
        return new WebClientGraphQLClient(webClient, headersConsumer, options);
    }
}
