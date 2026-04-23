/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.netflix.graphql.dgs.client

import com.netflix.graphql.dgs.json.DgsJsonMapper
import org.intellij.lang.annotations.Language
import org.springframework.http.HttpStatusCode
import reactor.core.publisher.Mono

/**
 * Reactive GraphQL client that delegates the HTTP call to a user-supplied [MonoRequestExecutor].
 *
 * The no-arg convenience constructor uses Jackson 3 under the hood. Callers on Jackson 2
 * must pass [Jackson2DgsJsonMapperAdapter] explicitly.
 */
class DgsCustomMonoGraphQLClient(
    private val url: String,
    private val monoRequestExecutor: MonoRequestExecutor,
    private val mapper: DgsJsonMapper,
) : DgsMonoGraphQLClient {
    constructor(
        url: String,
        monoRequestExecutor: MonoRequestExecutor,
    ) : this(url, monoRequestExecutor, Jackson2DgsJsonMapperAdapter.default())

    constructor(url: String, monoRequestExecutor: MonoRequestExecutor, options: DgsGraphQLRequestOptions) :
        this(url, monoRequestExecutor, Jackson2DgsJsonMapperAdapter.fromOptions(options))

    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
    ): Mono<DgsGraphQLResponse> = reactiveExecuteQuery(query, emptyMap(), null)

    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
    ): Mono<DgsGraphQLResponse> = reactiveExecuteQuery(query, variables, null)

    override fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): Mono<DgsGraphQLResponse> {
        val serializedRequest =
            mapper.writeValueAsString(
                GraphQLClients.toRequestMap(query = query, operationName = operationName, variables = variables),
            )
        return monoRequestExecutor.execute(url, GraphQLClients.defaultHeaders, serializedRequest).map { response ->
            if (HttpStatusCode.valueOf(response.statusCode).isError) {
                throw GraphQLClientException(response.statusCode, url, response.body ?: "", serializedRequest)
            }
            DefaultDgsGraphQLResponse(json = response.body ?: "", headers = response.headers, mapper)
        }
    }
}
