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

import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * A WebClient implementation of the DGS Client.
 * It does not implement [MonoGraphQLClient] because the user does not have to provide a request executor, but the API is the same otherwise.
 * A WebClient instance configured for the graphql endpoint (at least an url) must be provided.
 *
 * Example:
 *      WebClientGraphQLClient webClientGraphQLClient = new WebClientGraphQLClient(WebClient.create("http://localhost:8080/graphql"));
 *      GraphQLResponse message = webClientGraphQLClient.reactiveExecuteQuery("{hello}").map(r -> r.extractValue<String>("hello"));
 *      message.subscribe();
 */
class WebClientGraphQLClient(private val webclient: WebClient) : MonoGraphQLClient {
    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @return A [Mono] of [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    override fun reactiveExecuteQuery(
        query: String,
    ): Mono<GraphQLResponse> {
        return reactiveExecuteQuery(query, emptyMap(), null)
    }

    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @return A [Mono] of [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    override fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
    ): Mono<GraphQLResponse> {
        return reactiveExecuteQuery(query, variables, null)
    }

    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @param operationName Operation name
     * @return A [Mono] of [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    override fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): Mono<GraphQLResponse> {

        @Suppress("BlockingMethodInNonBlockingContext") val serializedRequest = GraphQLClients.objectMapper.writeValueAsString(
            Request(
                query,
                variables,
                operationName
            )
        )

        return webclient.post()
            .bodyValue(serializedRequest)
            .headers { consumer -> GraphQLClients.defaultHeaders.forEach(consumer::addAll) }
            .exchange()
            .flatMap { r ->
                r.bodyToMono(String::class.java)
                    .map { respBody -> HttpResponse(r.rawStatusCode(), respBody, r.headers().asHttpHeaders()) }
            }
            .map { httpResponse -> handleResponse(httpResponse, serializedRequest) }
    }

    private fun handleResponse(response: HttpResponse, requestBody: String): GraphQLResponse {
        val (statusCode, body) = response
        val headers = response.headers
        if (statusCode !in 200..299) {
            throw GraphQLClientException(statusCode, webclient.toString(), body ?: "", requestBody)
        }

        return GraphQLResponse(body ?: "", headers)
    }
}
