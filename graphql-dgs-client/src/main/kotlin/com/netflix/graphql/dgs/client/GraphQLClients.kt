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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

internal object GraphQLClients {

    internal val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json()
        .modulesToInstall(
            KotlinModule.Builder()
                .enable(KotlinFeature.NullIsSameAsDefault)
                .build()
        )
        .build()

    internal val defaultHeaders: HttpHeaders = HttpHeaders.readOnlyHttpHeaders(
        HttpHeaders().apply {
            accept = listOf(MediaType.APPLICATION_JSON)
            contentType = MediaType.APPLICATION_JSON
        }
    )

    fun handleResponse(response: HttpResponse, requestBody: String, url: String): GraphQLResponse {
        val (statusCode, body) = response
        val headers = response.headers
        if (HttpStatusCode.valueOf(response.statusCode).isError) {
            throw GraphQLClientException(statusCode, url, body ?: "", requestBody)
        }

        return GraphQLResponse(body ?: "", headers)
    }

    internal fun toRequestMap(query: String, operationName: String?, variables: Map<String, Any?>): Map<String, Any?> {
        return mapOf(
            "query" to query,
            "operationName" to operationName,
            "variables" to variables
        )
    }
}
