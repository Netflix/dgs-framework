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
import com.netflix.graphql.dgs.client.GraphQLResponse.GraphQLResponseOptions
import com.netflix.graphql.dgs.client.WebClientGraphQLClient.RequestBodyUriCustomizer
import org.intellij.lang.annotations.Language
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec
import org.springframework.web.reactive.function.client.toEntity
import reactor.core.publisher.Mono
import java.util.function.Consumer

/**
 * A WebClient implementation of the DGS Client.
 * It does not implement [MonoGraphQLClient] because the user does not have to provide a request executor, but the API is the same otherwise.
 * A WebClient instance configured for the graphql endpoint (at least an url) must be provided.
 *
 * Example:
 * ```java
 *      WebClientGraphQLClient webClientGraphQLClient =
 *          new WebClientGraphQLClient(WebClient.create("http://localhost:8080/graphql"));
 *      GraphQLResponse message = webClientGraphQLClient.reactiveExecuteQuery("{hello}")
 *                                                      .map(r -> r.extractValue<String>("hello"));
 *      message.subscribe();
 * ```
 */
class WebClientGraphQLClient(
    private val webclient: WebClient,
    private val headersConsumer: Consumer<HttpHeaders>,
    private val mapper: ObjectMapper,
    private val options: GraphQLResponseOptions? = null,
) : MonoGraphQLClient {
    constructor(webclient: WebClient) : this(webclient, Consumer {})

    constructor(webclient: WebClient, headersConsumer: Consumer<HttpHeaders>) : this(
        webclient,
        headersConsumer,
        GraphQLClients.objectMapper,
    )

    constructor(webclient: WebClient, mapper: ObjectMapper) : this(webclient, Consumer {}, mapper)

    constructor(
        webclient: WebClient,
        options: GraphQLResponseOptions? = null,
    ) : this(webclient, Consumer {}, GraphQLClients.objectMapper, options)

    constructor(
        webclient: WebClient,
        headersConsumer: Consumer<HttpHeaders>,
        options: GraphQLResponseOptions? = null,
    ) : this(webclient, headersConsumer, GraphQLClients.objectMapper, options)

    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @return A [Mono] of [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
    ): Mono<GraphQLResponse> = reactiveExecuteQuery(query, emptyMap(), null)

    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @return A [Mono] of [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
    ): Mono<GraphQLResponse> = reactiveExecuteQuery(query, variables, null)

    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @param operationName Operation name
     * @return A [Mono] of [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): Mono<GraphQLResponse> = reactiveExecuteQuery(query, variables, operationName, REQUEST_BODY_URI_CUSTOMIZER_IDENTITY)

    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param requestBodyUriCustomizer Allows customization of the URI and headers.
     *                                 This occurs before both, the [headersConsumer] and serialization of the GraphQL request to the body occurs.
     *                                 In other words, the [headersConsumer] will take precedence.
     * @return A [Mono] of [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        requestBodyUriCustomizer: RequestBodyUriCustomizer,
    ): Mono<GraphQLResponse> = reactiveExecuteQuery(query, emptyMap(), null, requestBodyUriCustomizer)

    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @param operationName GraphQL Operation name
     * @param requestBodyUriCustomizer Allows customization of the URI and headers.
     *                                 This occurs before both, the [headersConsumer] and serialization of the GraphQL request to the body occurs.
     *                                 In other words, the [headersConsumer] will take precedence.
     * @return A [Mono] of [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
        requestBodyUriCustomizer: RequestBodyUriCustomizer,
    ): Mono<GraphQLResponse> {
        val serializedRequest =
            mapper.writeValueAsString(
                GraphQLClients.toRequestMap(query = query, operationName = operationName, variables = variables),
            )

        return requestBodyUriCustomizer
            .apply(webclient.post())
            .headers { headers -> headers.addAll(GraphQLClients.defaultHeaders) }
            .headers(this.headersConsumer)
            .bodyValue(serializedRequest)
            .retrieve()
            .toEntity<String>()
            .map { httpResponse -> handleResponse(httpResponse, serializedRequest) }
    }

    private fun handleResponse(
        response: ResponseEntity<String>,
        requestBody: String,
    ): GraphQLResponse {
        if (!response.statusCode.is2xxSuccessful) {
            throw GraphQLClientException(
                statusCode = response.statusCode.value(),
                url = webclient.toString(),
                response = response.body ?: "",
                request = requestBody,
            )
        }

        return GraphQLResponse(json = response.body ?: "", headers = response.headers, options)
    }

    companion object {
        private val REQUEST_BODY_URI_CUSTOMIZER_IDENTITY = RequestBodyUriCustomizer { it }
    }

    @FunctionalInterface
    /**
     * Allows customization of the request URI and headers [WebClientGraphQLClient], returning the
     * modified [RequestBodySpec]. This could be used to set URI query parameters, for example:
     *
     * _Note the example uses Kotlin syntax_
     * ```
     * {  request ->
     *      request.uri{ uriBuilder ->
     *          uriBuilder
     *          .queryParam("q1", "foo")
     *          .build()
     *      }
     * }
     * ```
     */
    fun interface RequestBodyUriCustomizer {
        fun apply(spec: WebClient.RequestBodyUriSpec): RequestBodySpec
    }
}
