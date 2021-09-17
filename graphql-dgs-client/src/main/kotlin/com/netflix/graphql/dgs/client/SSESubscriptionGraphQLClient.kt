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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.types.subscription.QueryPayload
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToFlux
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*

class SSESubscriptionGraphQLClient(private val url: String, private val webClient: WebClient) : ReactiveGraphQLClient {

    private val mapper = jacksonObjectMapper()

    override fun reactiveExecuteQuery(query: String, variables: Map<String, Any>): Flux<GraphQLResponse> {
        return reactiveExecuteQuery(query, variables, null)
    }

    override fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
        operationName: String?
    ): Flux<GraphQLResponse> {
        val queryPayload = QueryPayload(variables, emptyMap(), operationName, query)

        val jsonPayload = mapper.writeValueAsString(queryPayload)

        return webClient.get()
            .uri("$url?query={query}", mapOf("query" to encodeQuery(jsonPayload)))
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .flatMapMany { r ->
                if (r.statusCode().is2xxSuccessful) {
                    r.bodyToFlux<String>().map { GraphQLResponse(it, r.headers().asHttpHeaders()) }.onBackpressureBuffer()
                } else {
                    if (r.statusCode().is4xxClientError || r.statusCode().is3xxRedirection) {
                        throw WebClientResponseException.create(r.rawStatusCode(), r.toString(), r.headers().asHttpHeaders(), byteArrayOf(), Charset.defaultCharset())
                    } else {
                        r.bodyToFlux<String>().map { throw WebClientResponseException.create(r.rawStatusCode(), r.toString(), r.headers().asHttpHeaders(), it.toByteArray(), Charset.defaultCharset()) }
                    }
                }
            }
            .publishOn(Schedulers.single())
    }

    private fun encodeQuery(query: String): String? {
        return Base64.getEncoder().encodeToString(query.toByteArray(StandardCharsets.UTF_8))
    }
}
