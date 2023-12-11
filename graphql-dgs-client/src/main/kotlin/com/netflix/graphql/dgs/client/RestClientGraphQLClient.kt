/*
 * Copyright 2023 Netflix, Inc.
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
import org.springframework.web.client.RestClient
import java.util.function.Consumer

/**
 * A RestClient implementation of the DGS Client for blocking use.
 * A RestClient instance configured for the graphql endpoint (at least an url) must be provided.
 *
 * Example:
 * ```java
 *      @Autowired
 *      RestClient.Builder restClientBuilder;
 *
 *      String helloRequest() {
 *          RestClient restClient = restClientBuilder.baseUrl("http://localhost:8080/graphql").build();
 *          RestClientGraphQLClient client = new RestClientGraphQLClient(restClient);
 *          return client.executeQuery("{hello}").extractValue<String>("hello");
 *      }
 * ```
 */
class RestClientGraphQLClient(
    private val restClient: RestClient,
    private val headersConsumer: Consumer<HttpHeaders>
) : GraphQLClient {

    constructor(restClient: RestClient) : this(restClient, Consumer { })

    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @return A [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    override fun executeQuery(@Language("graphql") query: String): GraphQLResponse {
        return executeQuery(query, emptyMap(), null)
    }

    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @return A [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    override fun executeQuery(@Language("graphql") query: String, variables: Map<String, Any>): GraphQLResponse {
        return executeQuery(query, variables, null)
    }

    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @param operationName Operation name
     * @return A [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    override fun executeQuery(@Language("graphql") query: String, variables: Map<String, Any>, operationName: String?): GraphQLResponse {
        val serializedRequest = GraphQLClients.objectMapper.writeValueAsString(
            Request(
                query,
                variables,
                operationName
            )
        )

        val responseEntity = restClient.post()
            .headers { headers -> headers.addAll(GraphQLClients.defaultHeaders) }
            .headers(this.headersConsumer)
            .body(serializedRequest)
            .retrieve()
            .toEntity(String::class.java)

        if (!responseEntity.statusCode.is2xxSuccessful) {
            throw GraphQLClientException(responseEntity.statusCode.value(), restClient.toString(), responseEntity.body ?: "", serializedRequest)
        }

        return GraphQLResponse(responseEntity.body ?: "", responseEntity.headers)
    }
}
