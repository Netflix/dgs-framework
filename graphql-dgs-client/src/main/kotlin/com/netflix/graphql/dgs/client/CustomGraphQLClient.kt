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

import com.fasterxml.jackson.databind.ObjectMapper
import org.intellij.lang.annotations.Language

/**
 * Blocking implementation of a GraphQL client.
 * The user is responsible for doing the actual HTTP request, making this pluggable with any HTTP client.
 * For a more convenient option, use [WebClientGraphQLClient] instead.
 */
class CustomGraphQLClient(private val url: String, private val requestExecutor: RequestExecutor, private val mapper: ObjectMapper) : GraphQLClient {

    constructor(url: String, requestExecutor: RequestExecutor) : this(url, requestExecutor, GraphQLClients.objectMapper)

    override fun executeQuery(@Language("graphql") query: String): GraphQLResponse {
        return executeQuery(query, emptyMap(), null)
    }

    override fun executeQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>
    ): GraphQLResponse {
        return executeQuery(query, variables, null)
    }

    override fun executeQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?
    ): GraphQLResponse {
        val serializedRequest = mapper.writeValueAsString(
            GraphQLClients.toRequestMap(query = query, operationName = operationName, variables = variables)
        )

        val response = requestExecutor.execute(url, GraphQLClients.defaultHeaders, serializedRequest)
        return GraphQLClients.handleResponse(response, serializedRequest, url)
    }
}
