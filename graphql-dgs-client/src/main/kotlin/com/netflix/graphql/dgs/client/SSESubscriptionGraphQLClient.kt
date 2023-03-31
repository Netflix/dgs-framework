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
import graphql.GraphQLException
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntityFlux
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import java.nio.charset.StandardCharsets
import java.util.*

private const val COMPLETE = "complete"

private const val NEXT = "next"

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
        val sink = Sinks.many().unicast().onBackpressureBuffer<GraphQLResponse>()

        val dis = webClient.get()
            .uri("$url?query={query}", mapOf("query" to encodeQuery(jsonPayload)))
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .toEntityFlux<ServerSentEvent<String>>()
            .flatMapMany { response ->
                val headers = response.headers
                response.body?.map { serverSentEvent ->
                    val data = serverSentEvent.data()
                    val event = serverSentEvent.event()
                    if (event == COMPLETE) {
                        sink.tryEmitComplete()
                    } else if (data != null && event == NEXT) {
                        sink.tryEmitNext(GraphQLResponse(json = data, headers = headers))
                    } else {
                        sink.tryEmitError(GraphQLException(String.format("Invalid SSE event, event type: %s, data: %s", event, data)))
                    }
                }
                    ?: Flux.empty()
            }.onErrorResume {
                Flux.just(sink.tryEmitError(it))
            }
            .doFinally {
                sink.tryEmitComplete()
            }.subscribeOn(Schedulers.boundedElastic()).subscribe()
        return sink.asFlux().publishOn(Schedulers.single()).doFinally {
            dis.dispose()
        }
    }

    private fun encodeQuery(query: String): String? {
        return Base64.getEncoder().encodeToString(query.toByteArray(StandardCharsets.UTF_8))
    }
}
