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

import reactor.core.publisher.Mono

interface GraphQLClient {
    fun executeQuery(query: String, variables: Map<String, Any>, requestExecutor: RequestExecutor): GraphQLResponse
    fun executeQuery(
        query: String,
        variables: Map<String, Any>,
        operationName: String?,
        requestExecutor: RequestExecutor
    ): GraphQLResponse
}

interface MonoGraphQLClient {
    fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
        requestExecutor: MonoRequestExecutor
    ): Mono<GraphQLResponse>

    fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
        operationName: String?,
        requestExecutor: MonoRequestExecutor
    ): Mono<GraphQLResponse>
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
