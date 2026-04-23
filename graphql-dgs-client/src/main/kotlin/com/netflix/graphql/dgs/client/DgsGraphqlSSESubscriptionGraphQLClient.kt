/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.netflix.graphql.dgs.client

import com.netflix.graphql.dgs.json.DgsJsonMapper
import com.netflix.graphql.types.subscription.QueryPayload
import org.intellij.lang.annotations.Language
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntityFlux
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers

/**
 * Client for servers following the graphql-sse spec
 * (https://github.com/graphql/graphql-over-http/blob/d51ae80d62b5fd8802a3383793f01bdf306e8290/rfcs/GraphQLOverSSE.md).
 *
 * The no-arg-mapper convenience constructor uses Jackson 3 under the hood. Callers on Jackson 2
 * must pass [Jackson2DgsJsonMapperAdapter] explicitly.
 */
class DgsGraphqlSSESubscriptionGraphQLClient(
    private val url: String,
    private val webClient: WebClient,
    private val mapper: DgsJsonMapper,
) : DgsReactiveGraphQLClient {
    constructor(url: String, webClient: WebClient) :
        this(url, webClient, Jackson2DgsJsonMapperAdapter.default())

    constructor(url: String, webClient: WebClient, options: DgsGraphQLRequestOptions) :
        this(url, webClient, Jackson2DgsJsonMapperAdapter.fromOptions(options))

    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
    ): Flux<out DgsGraphQLResponse> = reactiveExecuteQuery(query, variables, null)

    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): Flux<out DgsGraphQLResponse> {
        val queryPayload = QueryPayload(variables, emptyMap(), operationName, query)
        val jsonPayload = mapper.writeValueAsString(queryPayload)
        val sink = Sinks.many().unicast().onBackpressureBuffer<DgsGraphQLResponse>()

        val dis =
            webClient
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonPayload)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .toEntityFlux<String>()
                .flatMapMany {
                    val headers = it.headers.toMap()
                    it.body
                        ?.filter { sse -> sse.isNotBlank() }
                        ?.map { sse ->
                            sink.tryEmitNext(DefaultDgsGraphQLResponse(json = sse, headers = headers, mapper))
                        } ?: Flux.empty()
                }.onErrorResume { Flux.just(sink.tryEmitError(it)) }
                .doFinally { sink.tryEmitComplete() }
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe()
        return sink.asFlux().publishOn(Schedulers.single()).doFinally { dis.dispose() }
    }
}

private fun HttpHeaders.toMap(): Map<String, List<String>> {
    val result = mutableMapOf<String, List<String>>()
    this.forEach { key, values -> result[key] = values }
    return result
}
