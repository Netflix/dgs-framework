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

import com.fasterxml.jackson.databind.ObjectMapper
import org.intellij.lang.annotations.Language
import reactor.core.publisher.Mono

/**
 * Non-blocking implementation of a GraphQL client, based on the [Mono] type.
 * The user is responsible for doing the actual HTTP request, making this pluggable with any HTTP client.
 * For a more convenient option, use [WebClientGraphQLClient] instead.
 */
class CustomMonoGraphQLClient(
    private val url: String,
    private val monoRequestExecutor: MonoRequestExecutor,
    private val mapper: ObjectMapper,
    private val options: GraphQLRequestOptions? = null,
) : MonoGraphQLClient {
    constructor(
        url: String,
        monoRequestExecutor: MonoRequestExecutor,
    ) : this (url, monoRequestExecutor, GraphQLRequestOptions.createCustomObjectMapper())

    constructor(url: String, monoRequestExecutor: MonoRequestExecutor, mapper: ObjectMapper) : this(url, monoRequestExecutor, mapper, null)

    constructor(url: String, monoRequestExecutor: MonoRequestExecutor, options: GraphQLRequestOptions) : this(
        url,
        monoRequestExecutor,
        GraphQLRequestOptions.createCustomObjectMapper(options),
        options,
    )

    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
    ): Mono<GraphQLResponse> = reactiveExecuteQuery(query, emptyMap(), null)

    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
    ): Mono<GraphQLResponse> = reactiveExecuteQuery(query, variables, null)

    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): Mono<GraphQLResponse> {
        val serializedRequest =
            mapper.writeValueAsString(
                GraphQLClients.toRequestMap(query = query, operationName = operationName, variables = variables),
            )
        return monoRequestExecutor.execute(url, GraphQLClients.defaultHeaders, serializedRequest).map { response ->
            GraphQLClients.handleResponse(response, serializedRequest, url, options)
        }
    }
}
