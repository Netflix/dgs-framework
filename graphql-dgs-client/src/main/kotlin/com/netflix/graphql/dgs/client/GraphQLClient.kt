/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.graphql.dgs.client

import org.intellij.lang.annotations.Language

/**
 * GraphQL client interface for blocking clients.
 */
interface GraphQLClient {
    /**
     * A blocking call to execute a query and parse its result.
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @return [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun executeQuery(@Language("graphql") query: String): GraphQLResponse

    /**
     * A blocking call to execute a query and parse its result.
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @return [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun executeQuery(@Language("graphql") query: String, variables: Map<String, Any>): GraphQLResponse

    /**
     * A blocking call to execute a query and parse its result.
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @param operationName Name of the operation
     * @return [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun executeQuery(@Language("graphql") query: String, variables: Map<String, Any>, operationName: String?): GraphQLResponse

    @Deprecated(
        "The RequestExecutor should be provided while creating the implementation. Use CustomGraphQLClient/CustomMonoGraphQLClient instead.",
        ReplaceWith("Example: new CustomGraphQLClient(url, requestExecutor);")
    )
    fun executeQuery(query: String, variables: Map<String, Any>, requestExecutor: RequestExecutor): GraphQLResponse = throw UnsupportedOperationException()

    @Deprecated(
        "The RequestExecutor should be provided while creating the implementation. Use CustomGraphQLClient/CustomMonoGraphQLClient instead.",
        ReplaceWith("Example: new CustomGraphQLClient(url, requestExecutor);")
    )
    fun executeQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
        requestExecutor: RequestExecutor
    ): GraphQLResponse = throw UnsupportedOperationException()

    companion object {
        @JvmStatic
        fun createCustom(url: String, requestExecutor: RequestExecutor) = CustomGraphQLClient(url, requestExecutor)
    }
}
