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
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.ClassUtils

internal object GraphQLClients {

    internal val objectMapper: ObjectMapper =
        if (ClassUtils.isPresent("com.fasterxml.jackson.module.kotlin.KotlinModule\$Builder", this::class.java.classLoader)) {
            ObjectMapper().registerModule(KotlinModule.Builder().nullIsSameAsDefault(true).build())
        } else ObjectMapper().registerKotlinModule()

    internal val defaultHeaders: HttpHeaders = HttpHeaders.readOnlyHttpHeaders(
        HttpHeaders().apply {
            accept = listOf(MediaType.APPLICATION_JSON)
            contentType = MediaType.APPLICATION_JSON
        }
    )

    fun handleResponse(response: HttpResponse, requestBody: String, url: String): GraphQLResponse {
        val (statusCode, body) = response
        val headers = response.headers
        if (statusCode !in 200..299) {
            throw GraphQLClientException(statusCode, url, body ?: "", requestBody)
        }

        return GraphQLResponse(body ?: "", headers)
    }
}
