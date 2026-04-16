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
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity
import tools.jackson.databind.json.JsonMapper
import java.util.function.Consumer

/**
 * A RestClient implementation of the DGS Client for blocking use, using Jackson 3 for serialization.
 * A RestClient instance configured for the graphql endpoint (at least an url) must be provided.
 */
class Jackson3RestClientGraphQLClient(
    private val restClient: RestClient,
    private val headersConsumer: Consumer<HttpHeaders>,
    private val mapper: JsonMapper,
) : DgsGraphQLClient {
    constructor(restClient: RestClient) : this(restClient, Consumer { })

    constructor(restClient: RestClient, mapper: JsonMapper) : this(
        restClient,
        Consumer { },
        mapper,
    )

    constructor(restClient: RestClient, headersConsumer: Consumer<HttpHeaders>) : this(
        restClient,
        headersConsumer,
        Jackson3RequestOptions.createJsonMapper(),
    )

    constructor(restClient: RestClient, options: Jackson3RequestOptions) : this(
        restClient,
        Consumer { },
        Jackson3RequestOptions.createJsonMapper(options),
    )

    override fun executeQuery(
        @Language("graphql") query: String,
    ): GraphQLClientResponse = executeQuery(query, emptyMap(), null)

    override fun executeQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
    ): GraphQLClientResponse = executeQuery(query, variables, null)

    override fun executeQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): GraphQLClientResponse {
        val serializedRequest =
            mapper.writeValueAsString(
                GraphQLClients.toRequestMap(query = query, operationName = operationName, variables = variables),
            )

        val responseEntity =
            restClient
                .post()
                .headers { headers ->
                    GraphQLClients.defaultHeaders.forEach { (key, values) ->
                        headers.addAll(key, values)
                    }
                }.headers(this.headersConsumer)
                .body(serializedRequest)
                .retrieve()
                .toEntity<String>()

        if (!responseEntity.statusCode.is2xxSuccessful) {
            throw GraphQLClientException(
                statusCode = responseEntity.statusCode.value(),
                url = "",
                response = responseEntity.body ?: "",
                request = serializedRequest,
            )
        }

        return Jackson3GraphQLResponse(json = responseEntity.body ?: "", headers = responseEntity.headers.toMap(), mapper)
    }
}

private fun HttpHeaders.toMap(): Map<String, List<String>> {
    val result = mutableMapOf<String, List<String>>()
    this.forEach { key, values -> result[key] = values }
    return result
}
