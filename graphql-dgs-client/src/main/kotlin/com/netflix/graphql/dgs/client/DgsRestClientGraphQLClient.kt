/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.netflix.graphql.dgs.client

import com.netflix.graphql.dgs.json.DgsJsonMapper
import org.intellij.lang.annotations.Language
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity
import java.util.function.Consumer

/**
 * RestClient-based blocking DGS client.
 *
 * The no-arg convenience constructor uses Jackson 3 under the hood. Callers on Jackson 2
 * must pass [Jackson2DgsJsonMapperAdapter] explicitly.
 */
class DgsRestClientGraphQLClient(
    private val restClient: RestClient,
    private val headersConsumer: Consumer<HttpHeaders>,
    private val mapper: DgsJsonMapper,
) : DgsGraphQLClient {
    constructor(restClient: RestClient) : this(restClient, Consumer { })

    constructor(restClient: RestClient, headersConsumer: Consumer<HttpHeaders>) :
        this(restClient, headersConsumer, Jackson2DgsJsonMapperAdapter.default())

    constructor(restClient: RestClient, options: DgsGraphQLRequestOptions) :
        this(restClient, Consumer { }, Jackson2DgsJsonMapperAdapter.fromOptions(options))

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

        return DefaultDgsGraphQLResponse(json = responseEntity.body ?: "", headers = responseEntity.headers.toMap(), mapper)
    }
}

private fun HttpHeaders.toMap(): Map<String, List<String>> {
    val result = mutableMapOf<String, List<String>>()
    this.forEach { key, values -> result[key] = values }
    return result
}
