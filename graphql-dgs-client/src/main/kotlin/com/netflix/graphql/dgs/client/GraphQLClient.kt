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
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * GraphQL client interface for blocking clients.
 */
interface GraphQLClient {
    /**
     * A blocking call to execute a query and parse its result.
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @return [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun executeQuery(query: String): GraphQLResponse

    /**
     * A blocking call to execute a query and parse its result.
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @return [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun executeQuery(query: String, variables: Map<String, Any>): GraphQLResponse

    /**
     * A blocking call to execute a query and parse its result.
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @param operationName Name of the operation
     * @return [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun executeQuery(query: String, variables: Map<String, Any>, operationName: String?): GraphQLResponse

    @Deprecated("The RequestExecutor should be provided while creating the implementation.", ReplaceWith("Example: new DefaultGraphQLClient(url, requestExecutor);"))
    fun executeQuery(query: String, variables: Map<String, Any>, requestExecutor: RequestExecutor): GraphQLResponse = throw UnsupportedOperationException()

    @Deprecated("The RequestExecutor should be provided while creating the implementation.", ReplaceWith("Example: new DefaultGraphQLClient(url, requestExecutor);"))
    fun executeQuery(
        query: String,
        variables: Map<String, Any>,
        operationName: String?,
        requestExecutor: RequestExecutor
    ): GraphQLResponse = throw UnsupportedOperationException()

    companion object {
        fun createCustom(url: String, requestExecutor: RequestExecutor) = CustomGraphQLClient(url, requestExecutor)
    }
}

/**
 * GraphQL client interface for reactive clients.
 */
interface MonoGraphQLClient {
    /**
     * A reactive call to execute a query and parse its result.
     * Don't forget to subscribe() to actually send the query!
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @return A [Mono] of [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun reactiveExecuteQuery(
        query: String,
    ): Mono<GraphQLResponse>

    /**
     * A reactive call to execute a query and parse its result.
     * Don't forget to subscribe() to actually send the query!
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @return A [Mono] of [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
    ): Mono<GraphQLResponse>

    /**
     * A reactive call to execute a query and parse its result.
     * Don't forget to subscribe() to actually send the query!
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @param operationName Name of the operation
     * @return A [Mono] of [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): Mono<GraphQLResponse>

    @Deprecated("The RequestExecutor should be provided while creating the implementation.", ReplaceWith("Example: new DefaultGraphQLClient(url, requestExecutor);"))
    fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
        requestExecutor: MonoRequestExecutor
    ): Mono<GraphQLResponse> = throw UnsupportedOperationException()

    @Deprecated("The RequestExecutor should be provided while creating the implementation.", ReplaceWith("Example: new DefaultGraphQLClient(url, requestExecutor);"))
    fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
        operationName: String?,
        requestExecutor: MonoRequestExecutor
    ): Mono<GraphQLResponse> = throw UnsupportedOperationException()

    companion object {
        fun createCustomReactive(url: String, requestExecutor: MonoRequestExecutor) = CustomMonoGraphQLClient(url, requestExecutor)
        fun createWithWebClient(webClient: WebClient) = WebClientGraphQLClient(webClient)
    }
}

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
        query: String,
        variables: Map<String, Any>,
    ): Flux<GraphQLResponse>

    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @param operationName Operation name
     * @return A [Flux] of [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): Flux<GraphQLResponse>
}

@FunctionalInterface
/**
 * Code responsible for executing the HTTP request for a GraphQL query.
 * Typically provided as a lambda.
 * @param url The URL the client was configured with
 * @param headers A map of headers. The client sets some default headers such as Accept and Content-Type.
 * @param body The request body
 * @returns HttpResponse which is a representation of the HTTP status code and the response body as a String.
 */
fun interface RequestExecutor {
    fun execute(url: String, headers: Map<String, List<String>>, body: String): HttpResponse
}

data class HttpResponse(val statusCode: Int, val body: String?, val headers: Map<String, List<String>>) {
    constructor(statusCode: Int, body: String?) : this(statusCode, body, emptyMap())
}

@FunctionalInterface
/**
 * Code responsible for executing the HTTP request for a GraphQL query.
 * Typically provided as a lambda.  Reactive version (Mono)
 * @param url The URL the client was configured with
 * @param headers A map of headers. The client sets some default headers such as Accept and Content-Type.
 * @param body The request body
 * @returns Mono<HttpResponse> which is a representation of the HTTP status code and the response body as a String.
 */
fun interface MonoRequestExecutor {
    fun execute(url: String, headers: Map<String, List<String>>, body: String): Mono<HttpResponse>
}

/**
 * A transport level exception (e.g. a failed connection). This does *not* represent successful GraphQL responses that contain errors.
 */
class GraphQLClientException(statusCode: Int, url: String, response: String, request: String) :
    RuntimeException("GraphQL server $url responded with status code $statusCode: '$response'. The request sent to the server was \n$request")

internal object GraphQLClients {
    internal val objectMapper: ObjectMapper = try {
        Class.forName("com.fasterxml.jackson.module.kotlin.KotlinModule\$Builder")
        ObjectMapper().registerModule(KotlinModule.Builder().nullIsSameAsDefault(true).build())
    } catch (ex: ClassNotFoundException) {
        ObjectMapper().registerKotlinModule()
    }
    internal val defaultHeaders = mapOf(
        "Accept" to listOf("application/json"),
        "Content-type" to listOf("application/json")
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

internal data class Request(val query: String, val variables: Map<String, Any>, val operationName: String?)
