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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.types.subscription.QueryPayload
import org.intellij.lang.annotations.Language
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntityFlux
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers

/*
 * This client can be used for servers which are following the graphql-sse specification, which can be found here:
 * https://github.com/graphql/graphql-over-http/blob/d51ae80d62b5fd8802a3383793f01bdf306e8290/rfcs/GraphQLOverSSE.md
 */
class GraphqlSSESubscriptionGraphQLClient(
    private val url: String,
    private val webClient: WebClient,
) : ReactiveGraphQLClient {
    private val mapper = jacksonObjectMapper()

    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
    ): Flux<GraphQLResponse> = reactiveExecuteQuery(query, variables, null)

    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): Flux<GraphQLResponse> {
        val queryPayload = QueryPayload(variables, emptyMap(), operationName, query)

        val jsonPayload = mapper.writeValueAsString(queryPayload)
        val sink = Sinks.many().unicast().onBackpressureBuffer<GraphQLResponse>()

        val dis =
            webClient
                .post()
                .uri(url)
                .bodyValue(jsonPayload)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .toEntityFlux<String>()
                .flatMapMany {
                    val headers = it.headers
                    it.body?.map { serverSentEvent ->
                        sink.tryEmitNext(GraphQLResponse(json = serverSentEvent, headers = headers))
                    } ?: Flux.empty()
                }.onErrorResume {
                    Flux.just(sink.tryEmitError(it))
                }.doFinally {
                    sink.tryEmitComplete()
                }.subscribeOn(Schedulers.boundedElastic())
                .subscribe()
        return sink.asFlux().publishOn(Schedulers.single()).doFinally {
            dis.dispose()
        }
    }
}
