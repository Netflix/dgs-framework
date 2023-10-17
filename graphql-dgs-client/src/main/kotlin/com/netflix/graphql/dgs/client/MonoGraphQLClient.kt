/*
 * Copyright 2022 Netflix, Inc.
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
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.function.Consumer

/**
 * GraphQL client interface for reactive clients.
 */
interface MonoGraphQLClient {
    /**
     * A reactive call to execute a query and parse its result.
     * Don't forget to subscribe() to actually send the query!
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @return A [Mono] of [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun reactiveExecuteQuery(
        @Language("graphql") query: String
    ): Mono<GraphQLResponse>

    /**
     * A reactive call to execute a query and parse its result.
     * Don't forget to subscribe() to actually send the query!
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @return A [Mono] of [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>
    ): Mono<GraphQLResponse>

    /**
     * A reactive call to execute a query and parse its result.
     * Don't forget to subscribe() to actually send the query!
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @param operationName Name of the operation
     * @return A [Mono] of [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?
    ): Mono<GraphQLResponse>

    @Deprecated(
        "The RequestExecutor should be provided while creating the implementation. Use CustomGraphQLClient/CustomMonoGraphQLClient instead.",
        ReplaceWith("Example: new CustomGraphQLClient(url, requestExecutor);")
    )
    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        requestExecutor: MonoRequestExecutor
    ): Mono<GraphQLResponse> = throw UnsupportedOperationException()

    @Deprecated(
        "The RequestExecutor should be provided while creating the implementation. Use CustomGraphQLClient/CustomMonoGraphQLClient instead.",
        ReplaceWith("Example: new CustomGraphQLClient(url, requestExecutor);")
    )
    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
        requestExecutor: MonoRequestExecutor
    ): Mono<GraphQLResponse> = throw UnsupportedOperationException()

    companion object {
        @JvmStatic
        fun createCustomReactive(
            @Language("url") url: String,
            requestExecutor: MonoRequestExecutor
        ) = CustomMonoGraphQLClient(url, requestExecutor)

        @JvmStatic
        fun createWithWebClient(webClient: WebClient) = WebClientGraphQLClient(webClient)

        @JvmStatic
        fun createWithWebClient(
            webClient: WebClient,
            headersConsumer: Consumer<HttpHeaders>
        ) = WebClientGraphQLClient(webClient, headersConsumer)
    }
}
