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
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec
import org.springframework.web.reactive.function.client.toEntity
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import java.util.function.Consumer

/**
 * A WebClient implementation of the DGS Client using Jackson 3 for serialization.
 * A WebClient instance configured for the graphql endpoint (at least an url) must be provided.
 */
class Jackson3WebClientGraphQLClient(
    private val webclient: WebClient,
    private val headersConsumer: Consumer<HttpHeaders>,
    private val mapper: JsonMapper,
) : DgsMonoGraphQLClient {
    constructor(webclient: WebClient) : this(webclient, Consumer {})

    constructor(webclient: WebClient, headersConsumer: Consumer<HttpHeaders>) : this(
        webclient,
        headersConsumer,
        Jackson3RequestOptions.createJsonMapper(),
    )

    constructor(
        webclient: WebClient,
        options: Jackson3RequestOptions,
    ) : this(webclient, Consumer {}, Jackson3RequestOptions.createJsonMapper(options))

    constructor(webclient: WebClient, mapper: JsonMapper) : this(
        webclient,
        Consumer {},
        mapper,
    )

    constructor(
        webclient: WebClient,
        headersConsumer: Consumer<HttpHeaders>,
        options: Jackson3RequestOptions,
    ) : this(webclient, headersConsumer, Jackson3RequestOptions.createJsonMapper(options))

    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
    ): Mono<GraphQLClientResponse> = reactiveExecuteQuery(query, emptyMap(), null)

    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
    ): Mono<GraphQLClientResponse> = reactiveExecuteQuery(query, variables, null)

    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): Mono<GraphQLClientResponse> = reactiveExecuteQuery(query, variables, operationName, REQUEST_BODY_URI_CUSTOMIZER_IDENTITY)

    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        requestBodyUriCustomizer: RequestBodyUriCustomizer,
    ): Mono<GraphQLClientResponse> = reactiveExecuteQuery(query, emptyMap(), null, requestBodyUriCustomizer)

    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
        requestBodyUriCustomizer: RequestBodyUriCustomizer,
    ): Mono<GraphQLClientResponse> {
        val serializedRequest =
            mapper.writeValueAsString(
                GraphQLClients.toRequestMap(query = query, operationName = operationName, variables = variables),
            )

        return requestBodyUriCustomizer
            .apply(webclient.post())
            .headers { headers ->
                GraphQLClients.defaultHeaders.forEach { (key, values) ->
                    headers.addAll(key, values)
                }
            }.headers(this.headersConsumer)
            .bodyValue(serializedRequest)
            .retrieve()
            .toEntity<String>()
            .map { httpResponse -> handleResponse(httpResponse, serializedRequest) }
    }

    private fun handleResponse(
        response: ResponseEntity<String>,
        requestBody: String,
    ): GraphQLClientResponse {
        if (!response.statusCode.is2xxSuccessful) {
            throw GraphQLClientException(
                statusCode = response.statusCode.value(),
                url = webclient.toString(),
                response = response.body ?: "",
                request = requestBody,
            )
        }

        return Jackson3GraphQLResponse(json = response.body ?: "", headers = response.headers.toMap(), mapper)
    }

    @FunctionalInterface
    fun interface RequestBodyUriCustomizer {
        fun apply(spec: WebClient.RequestBodyUriSpec): RequestBodySpec
    }

    companion object {
        private val REQUEST_BODY_URI_CUSTOMIZER_IDENTITY = RequestBodyUriCustomizer { it }
    }
}

private fun HttpHeaders.toMap(): Map<String, List<String>> {
    val result = mutableMapOf<String, List<String>>()
    this.forEach { key, values -> result[key] = values }
    return result
}
