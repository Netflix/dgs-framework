/*
 * Copyright 2026 Netflix, Inc.
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
import org.springframework.http.HttpStatusCode
import tools.jackson.databind.json.JsonMapper

/**
 * Blocking implementation of a GraphQL client using Jackson 3 for serialization.
 * The user is responsible for doing the actual HTTP request, making this pluggable with any HTTP client.
 */
class Jackson3CustomGraphQLClient(
    private val url: String,
    private val requestExecutor: RequestExecutor,
    private val mapper: JsonMapper,
) : DgsGraphQLClient {
    constructor(
        url: String,
        requestExecutor: RequestExecutor,
    ) : this(url, requestExecutor, Jackson3RequestOptions.createJsonMapper())

    constructor(url: String, requestExecutor: RequestExecutor, options: Jackson3RequestOptions) : this(
        url,
        requestExecutor,
        Jackson3RequestOptions.createJsonMapper(options),
    )

    override fun executeQuery(
        @Language("graphql") query: String,
    ): GraphQLClientResponse = executeQuery(query, emptyMap(), null)

    override fun executeQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
    ): GraphQLClientResponse = executeQuery(query, variables, null)

    override fun executeQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): GraphQLClientResponse {
        val serializedRequest =
            mapper.writeValueAsString(
                GraphQLClients.toRequestMap(query = query, operationName = operationName, variables = variables),
            )

        val response = requestExecutor.execute(url, GraphQLClients.defaultHeaders, serializedRequest)
        return handleResponse(response, serializedRequest, url)
    }

    private fun handleResponse(
        response: HttpResponse,
        requestBody: String,
        url: String,
    ): GraphQLClientResponse {
        if (HttpStatusCode.valueOf(response.statusCode).isError) {
            throw GraphQLClientException(response.statusCode, url, response.body ?: "", requestBody)
        }
        return Jackson3GraphQLResponse(json = response.body ?: "", headers = response.headers, mapper)
    }
}
