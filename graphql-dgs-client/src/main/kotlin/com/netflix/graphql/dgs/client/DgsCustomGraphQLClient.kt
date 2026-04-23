/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.netflix.graphql.dgs.client

import com.netflix.graphql.dgs.json.DgsJsonMapper
import org.intellij.lang.annotations.Language
import org.springframework.http.HttpStatusCode

/**
 * Blocking GraphQL client that delegates the HTTP call to a user-supplied [RequestExecutor].
 *
 * The no-arg convenience constructor uses Jackson 2 (the only Jackson available on DGS 10.x).
 */
class DgsCustomGraphQLClient(
    private val url: String,
    private val requestExecutor: RequestExecutor,
    private val mapper: DgsJsonMapper,
) : DgsGraphQLClient {
    constructor(
        url: String,
        requestExecutor: RequestExecutor,
    ) : this(url, requestExecutor, Jackson2DgsJsonMapperAdapter.default())

    constructor(url: String, requestExecutor: RequestExecutor, options: DgsGraphQLRequestOptions) :
        this(url, requestExecutor, Jackson2DgsJsonMapperAdapter.fromOptions(options))

    override fun executeQuery(
        @Language("graphql") query: String,
    ): DgsGraphQLResponse = executeQuery(query, emptyMap(), null)

    override fun executeQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
    ): DgsGraphQLResponse = executeQuery(query, variables, null)

    override fun executeQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): DgsGraphQLResponse {
        val serializedRequest =
            mapper.writeValueAsString(
                GraphQLClients.toRequestMap(query = query, operationName = operationName, variables = variables),
            )

        val response = requestExecutor.execute(url, GraphQLClients.defaultHeaders, serializedRequest)
        if (HttpStatusCode.valueOf(response.statusCode).isError) {
            throw GraphQLClientException(response.statusCode, url, response.body ?: "", serializedRequest)
        }
        return DefaultDgsGraphQLResponse(json = response.body ?: "", headers = response.headers, mapper)
    }
}
