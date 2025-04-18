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

package com.netflix.graphql.dgs.client

import org.intellij.lang.annotations.Language
import reactor.core.publisher.Flux

/**
 * GraphQL client interface for reactive clients that support multiple results such as subscriptions.
 */
interface ReactiveGraphQLClient {
    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @return A [Flux] of [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
    ): Flux<GraphQLResponse>

    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @param operationName Operation name
     * @return A [Flux] of [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): Flux<GraphQLResponse>
}
