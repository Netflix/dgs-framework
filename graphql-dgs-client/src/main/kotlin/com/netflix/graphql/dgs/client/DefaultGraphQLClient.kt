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

/**
 * Default [GraphQLClient] implementation. Use this class to execute GraphQL queries against a standalone DGS or the gateway.
 * The value of this client is in it's JSON parsing of responses.
 * The client is not tied to any particular HTTP client library. The actual HTTP request code is provided by the user.
 * Note that if you want to use WebClient, there is the [WebClientGraphQLClient] available, which is simpler to use.
 *
 * Example:
 *
 *     @Autowired
 *     RestTemplate restTemplate;
 *
 *     DefaultGraphQLClient graphQLClient = new DefaultGraphQLClient("/graphql");
 *       return graphQLClient.executeQuery(query, Collections.emptyMap(), (url, headers, body) -> {
 *       HttpHeaders httpHeaders = new HttpHeaders();
 *       headers.forEach(httpHeaders::addAll);
 *
 *       ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<String>(body, httpHeaders), String.class);
 *       return new HttpResponse(exchange.getStatusCodeValue(), exchange.getBody());
 *    });
 */
@Deprecated("This has been replaced by [CustomGraphQLClient], [CustomReactiveGraphQLClient] and [WebClientGraphQLClient]")
class DefaultGraphQLClient(private val url: String) : GraphQLClient, MonoGraphQLClient {

    /**
     * Executes a query and returns a GraphQLResponse.
     * The actual HTTP request is done by an implementation of RequestExecutor, which is user provided.
     * The RequestExecutor is typically provided as a lambda expression.
     * The `Accept` and `Content-Type` headers are set. Additional headers can be set in the RequestExecutor.
     * @param query The Query as a String
     * @param variables Query variables. May be empty
     * @param operationName optional operation name
     * @param requestExecutor The code that does the actual HTTP request. Typically provided as a lambda expression.
     * @return GraphQLResponse
     * @throws GraphQLClientException when the HTTP response code is not 2xx.
     */
    override fun executeQuery(
        query: String,
        variables: Map<String, Any>,
        operationName: String?,
        requestExecutor: RequestExecutor
    ): GraphQLResponse {
        val serializedRequest = GraphQLClients.objectMapper.writeValueAsString(Request(query, variables, operationName))
        val response = requestExecutor.execute(url, GraphQLClients.defaultHeaders, serializedRequest)
        return GraphQLClients.handleResponse(response, serializedRequest, url)
    }

    override fun executeQuery(query: String): GraphQLResponse {
        throw UnsupportedOperationException("Please move to [BlockingGraphQLClient] to use this method")
    }

    override fun executeQuery(query: String, variables: Map<String, Any>): GraphQLResponse {
        throw UnsupportedOperationException("Please move to [BlockingGraphQLClient] to use this method")
    }

    override fun executeQuery(query: String, variables: Map<String, Any>, operationName: String?): GraphQLResponse {
        throw UnsupportedOperationException("Please move to [BlockingGraphQLClient] to use this method")
    }

    /**
     * Executes a query and returns a GraphQLResponse.
     * The actual HTTP request is done by an implementation of RequestExecutor, which is user provided.
     * The RequestExecutor is typically provided as a lambda expression.
     * The `Accept` and `Content-Type` headers are set. Additional headers can be set in the RequestExecutor.
     * @param query The Query as a String
     * @param variables Query variables. May be empty
     * @param requestExecutor The code that does the actual HTTP request. Typically provided as a lambda expression.
     * @return GraphQLResponse
     * @throws GraphQLClientException when the HTTP response code is not 2xx.
     */
    override fun executeQuery(
        query: String,
        variables: Map<String, Any>,
        requestExecutor: RequestExecutor
    ): GraphQLResponse {
        return executeQuery(query, variables, null, requestExecutor)
    }

    override fun reactiveExecuteQuery(query: String): Mono<GraphQLResponse> {
        throw UnsupportedOperationException("Please move to [CustomGraphQLClient] to use this method")
    }

    override fun reactiveExecuteQuery(query: String, variables: Map<String, Any>): Mono<GraphQLResponse> {
        throw UnsupportedOperationException("Please move to [CustomGraphQLClient] to use this method")
    }

    override fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
        operationName: String?
    ): Mono<GraphQLResponse> {
        throw UnsupportedOperationException("Please move to [CustomGraphQLClient] to use this method")
    }

    /**
     * Executes a query and returns a reactive Mono<GraphQLResponse>.
     * The actual HTTP request is done by an implementation of RequestExecutor, which is user provided.
     * The RequestExecutor is typically provided as a lambda expression.
     * The `Accept` and `Content-Type` headers are set. Additional headers can be set in the RequestExecutor.
     * @param query The Query as a String
     * @param variables Query variables. May be empty
     * @param requestExecutor The code that does the actual HTTP request. Typically provided as a lambda expression.
     * @return Mono<GraphQLResponse>
     * @throws GraphQLClientException when the HTTP response code is not 2xx.
     */
    override fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
        requestExecutor: MonoRequestExecutor
    ): Mono<GraphQLResponse> {
        return reactiveExecuteQuery(query, variables, null, requestExecutor)
    }

    /**
     * Executes a query and returns a reactive Mono<GraphQLResponse>.
     * The actual HTTP request is done by an implementation of RequestExecutor, which is user provided.
     * The RequestExecutor is typically provided as a lambda expression.
     * The `Accept` and `Content-Type` headers are set. Additional headers can be set in the RequestExecutor.
     * @param query The Query as a String
     * @param variables Query variables. May be empty
     * @param operationName optional operation name
     * @param requestExecutor The code that does the actual HTTP request. Typically provided as a lambda expression.
     * @return Mono<GraphQLResponse>
     * @throws GraphQLClientException when the HTTP response code is not 2xx.
     */
    override fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
        operationName: String?,
        requestExecutor: MonoRequestExecutor
    ): Mono<GraphQLResponse> {
        val serializedRequest = GraphQLClients.objectMapper.writeValueAsString(Request(query, variables, operationName))
        return requestExecutor.execute(url, GraphQLClients.defaultHeaders, serializedRequest).map { response ->
            GraphQLClients.handleResponse(response, serializedRequest, url)
        }
    }
}
