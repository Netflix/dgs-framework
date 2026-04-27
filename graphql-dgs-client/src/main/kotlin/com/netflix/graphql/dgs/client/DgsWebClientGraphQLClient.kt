/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.netflix.graphql.dgs.client

import com.netflix.graphql.dgs.json.DgsJsonMapper
import org.intellij.lang.annotations.Language
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec
import org.springframework.web.reactive.function.client.toEntity
import reactor.core.publisher.Mono
import java.util.function.Consumer

/**
 * WebClient-based reactive DGS client.
 *
 * The no-arg convenience constructor uses Jackson 2 (the only Jackson available on DGS 10.x).
 */
class DgsWebClientGraphQLClient(
    private val webclient: WebClient,
    private val headersConsumer: Consumer<HttpHeaders>,
    private val mapper: DgsJsonMapper,
) : DgsMonoGraphQLClient {
    constructor(webclient: WebClient) : this(webclient, Consumer {})

    constructor(webclient: WebClient, headersConsumer: Consumer<HttpHeaders>) :
        this(webclient, headersConsumer, Jackson2DgsJsonMapperAdapter.default())

    constructor(webclient: WebClient, options: DgsGraphQLRequestOptions) :
        this(webclient, Consumer {}, Jackson2DgsJsonMapperAdapter.fromOptions(options))

    constructor(webclient: WebClient, headersConsumer: Consumer<HttpHeaders>, options: DgsGraphQLRequestOptions) :
        this(webclient, headersConsumer, Jackson2DgsJsonMapperAdapter.fromOptions(options))

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
    ): Mono<DgsGraphQLResponse> = reactiveExecuteQuery(query, variables, operationName, REQUEST_BODY_URI_CUSTOMIZER_IDENTITY)

    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        requestBodyUriCustomizer: RequestBodyUriCustomizer,
    ): Mono<DgsGraphQLResponse> = reactiveExecuteQuery(query, emptyMap(), null, requestBodyUriCustomizer)

    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
        requestBodyUriCustomizer: RequestBodyUriCustomizer,
    ): Mono<DgsGraphQLResponse> {
        val serializedRequest =
            mapper.writeValueAsString(
                GraphQLClients.toRequestMap(query = query, operationName = operationName, variables = variables),
            )

        return requestBodyUriCustomizer
            .apply(webclient.post())
            .headers { headers ->
                GraphQLClients.defaultHeaders.forEach { (key, values) ->
                    headers.addAll(key, values)
                }
            }.headers(this.headersConsumer)
            .bodyValue(serializedRequest)
            .retrieve()
            .toEntity<String>()
            .map { httpResponse -> handleResponse(httpResponse, serializedRequest) }
    }

    private fun handleResponse(
        response: ResponseEntity<String>,
        requestBody: String,
    ): DgsGraphQLResponse {
        if (!response.statusCode.is2xxSuccessful) {
            throw GraphQLClientException(
                statusCode = response.statusCode.value(),
                url = webclient.toString(),
                response = response.body ?: "",
                request = requestBody,
            )
        }
        return DefaultDgsGraphQLResponse(json = response.body ?: "", headers = response.headers.toMap(), mapper)
    }

    @FunctionalInterface
    fun interface RequestBodyUriCustomizer {
        fun apply(spec: WebClient.RequestBodyUriSpec): RequestBodySpec
    }

    companion object {
        private val REQUEST_BODY_URI_CUSTOMIZER_IDENTITY = RequestBodyUriCustomizer { it }
    }
}

private fun HttpHeaders.toMap(): Map<String, List<String>> {
    val result = mutableMapOf<String, List<String>>()
    this.forEach { key, values -> result[key] = values }
    return result
}
