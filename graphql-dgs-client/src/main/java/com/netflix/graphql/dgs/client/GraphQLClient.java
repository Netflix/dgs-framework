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

import java.util.Map;

/**
 * GraphQL client interface for blocking clients.
 *
 * @deprecated Tied to Jackson 2 through {@link GraphQLResponse}. Program against {@link DgsGraphQLClient} instead.
 *             This interface will be removed in a future release.
 */
@Deprecated
public interface GraphQLClient extends DgsGraphQLClient {
    /**
     * A blocking call to execute a query and parse its result.
     *
     * @param query The query string. Note that you can use code generation for a type safe query!
     * @return {@link GraphQLResponse} parses the response and gives easy access to data and errors.
     */
    @NotNull
    @Override
    GraphQLResponse executeQuery(@NotNull @Language("graphql") String query);

    /**
     * A blocking call to execute a query and parse its result.
     *
     * @param query The query string. Note that you can use code generation for a type safe query!
     * @param variables A map of input variables
     * @return {@link GraphQLResponse} parses the response and gives easy access to data and errors.
     */
    @NotNull
    @Override
    GraphQLResponse executeQuery(@NotNull @Language("graphql") String query, @NotNull Map<String, ? extends Object> variables);

    /**
     * A blocking call to execute a query and parse its result.
     *
     * @param query The query string. Note that you can use code generation for a type safe query!
     * @param variables A map of input variables
     * @param operationName Name of the operation
     * @return {@link GraphQLResponse} parses the response and gives easy access to data and errors.
     */
    @NotNull
    @Override
    GraphQLResponse executeQuery(
            @NotNull @Language("graphql") String query, @NotNull Map<String, ? extends Object> variables, @Nullable String operationName);

    /**
     * @deprecated The RequestExecutor should be provided while creating the implementation.
     *             Use CustomGraphQLClient/CustomMonoGraphQLClient instead.
     */
    @NotNull
    @Deprecated
    default GraphQLResponse executeQuery(
            @NotNull String query, @NotNull Map<String, ? extends Object> variables, @NotNull RequestExecutor requestExecutor) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated The RequestExecutor should be provided while creating the implementation.
     *             Use CustomGraphQLClient/CustomMonoGraphQLClient instead.
     */
    @NotNull
    @Deprecated
    default GraphQLResponse executeQuery(
            @NotNull @Language("graphql") String query,
            @NotNull Map<String, ? extends Object> variables,
            @Nullable String operationName,
            @NotNull RequestExecutor requestExecutor) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    static CustomGraphQLClient createCustom(@NotNull String url, @NotNull RequestExecutor requestExecutor) {
        return new CustomGraphQLClient(url, requestExecutor);
    }

    @NotNull
    static CustomGraphQLClient createCustom(@NotNull String url, @NotNull RequestExecutor requestExecutor, @NotNull ObjectMapper mapper) {
        return new CustomGraphQLClient(url, requestExecutor, mapper);
    }

    @NotNull
    static CustomGraphQLClient createCustom(
            @NotNull String url, @NotNull RequestExecutor requestExecutor, @NotNull GraphQLRequestOptions options) {
        return new CustomGraphQLClient(url, requestExecutor, options);
    }
}
