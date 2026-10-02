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
    @NotNull
    @Override
    Mono<GraphQLResponse> reactiveExecuteQuery(@NotNull @Language("graphql") String query);

    @NotNull
    @Override
    Mono<GraphQLResponse> reactiveExecuteQuery(@NotNull @Language("graphql") String query, @NotNull Map<String, ? extends Object> variables);

    @NotNull
    @Override
    Mono<GraphQLResponse> reactiveExecuteQuery(
            @NotNull @Language("graphql") String query, @NotNull Map<String, ? extends Object> variables, @Nullable String operationName);

    /**
     * @deprecated The RequestExecutor should be provided while creating the implementation.
     *             Use CustomGraphQLClient/CustomMonoGraphQLClient instead.
     */
    @NotNull
    @Deprecated
    default Mono<GraphQLResponse> reactiveExecuteQuery(
            @NotNull @Language("graphql") String query, @NotNull Map<String, ? extends Object> variables, @NotNull MonoRequestExecutor requestExecutor) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated The RequestExecutor should be provided while creating the implementation.
     *             Use CustomGraphQLClient/CustomMonoGraphQLClient instead.
     */
    @NotNull
    @Deprecated
    default Mono<GraphQLResponse> reactiveExecuteQuery(
            @NotNull @Language("graphql") String query,
            @NotNull Map<String, ? extends Object> variables,
            @Nullable String operationName,
            @NotNull MonoRequestExecutor requestExecutor) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    static CustomMonoGraphQLClient createCustomReactive(
            @NotNull @Language("url") String url, @NotNull MonoRequestExecutor requestExecutor) {
        return new CustomMonoGraphQLClient(url, requestExecutor);
    }

    @NotNull
    static CustomMonoGraphQLClient createCustomReactive(
            @NotNull @Language("url") String url, @NotNull MonoRequestExecutor requestExecutor, @NotNull GraphQLRequestOptions options) {
        return new CustomMonoGraphQLClient(url, requestExecutor, options);
    }

    @NotNull
    static WebClientGraphQLClient createWithWebClient(@NotNull WebClient webClient) {
        return new WebClientGraphQLClient(webClient);
    }

    @NotNull
    static WebClientGraphQLClient createWithWebClient(@NotNull WebClient webClient, @NotNull ObjectMapper objectMapper) {
        return new WebClientGraphQLClient(webClient, objectMapper);
    }

    @NotNull
    static WebClientGraphQLClient createWithWebClient(@NotNull WebClient webClient, @NotNull Consumer<HttpHeaders> headersConsumer) {
        return new WebClientGraphQLClient(webClient, headersConsumer);
    }

    @NotNull
    static WebClientGraphQLClient createWithWebClient(@NotNull WebClient webClient, @NotNull GraphQLRequestOptions options) {
        return new WebClientGraphQLClient(webClient, headers -> { }, options);
    }

    @NotNull
    static WebClientGraphQLClient createWithWebClient(
            @NotNull WebClient webClient, @NotNull Consumer<HttpHeaders> headersConsumer, @NotNull GraphQLRequestOptions options) {
        return new WebClientGraphQLClient(webClient, headersConsumer, options);
    }
}
