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

import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

internal object GraphQLClients {
    @Deprecated(message = "Use GraphQLRequestOptions.createCustomObjectMapper instead")
    internal val objectMapper: JsonMapper =
        JsonMapper
            .builder()
            .addModule(
                KotlinModule
                    .Builder()
                    .enable(KotlinFeature.NullIsSameAsDefault)
                    .build(),
            ).build()

    internal val defaultHeaders: Map<String, List<String>> =
        mapOf(
            "Accept" to listOf(MediaType.APPLICATION_JSON.toString()),
            "Content-Type" to listOf(MediaType.APPLICATION_JSON.toString()),
        )

    fun handleResponse(
        response: HttpResponse,
        requestBody: String,
        url: String,
    ): GraphQLResponse = handleResponse(response, requestBody, url, null)

    fun handleResponse(
        response: HttpResponse,
        requestBody: String,
        url: String,
        mapper: JsonMapper,
    ): GraphQLResponse {
        val statusCode = response.statusCode
        val body = response.body
        if (HttpStatusCode.valueOf(response.statusCode).isError) {
            throw GraphQLClientException(statusCode, url, body ?: "", requestBody)
        }

        return GraphQLResponse(body ?: "", response.headers, mapper)
    }

    fun handleResponse(
        response: HttpResponse,
        requestBody: String,
        url: String,
        options: GraphQLRequestOptions? = null,
    ): GraphQLResponse {
        val statusCode = response.statusCode
        val body = response.body
        if (HttpStatusCode.valueOf(response.statusCode).isError) {
            throw GraphQLClientException(statusCode, url, body ?: "", requestBody)
        }

        return GraphQLResponse(body ?: "", response.headers, options)
    }

    internal fun toRequestMap(
        query: String,
        operationName: String?,
        variables: Map<String, Any?>,
    ): Map<String, Any?> =
        mapOf(
            "query" to query,
            "operationName" to operationName,
            "variables" to variables,
        )
}
