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

package com.netflix.graphql.dgs.webflux.handlers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import graphql.ExecutionResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

class DgsWebfluxHttpHandler(private val dgsQueryExecutor: DgsReactiveQueryExecutor) {

    fun graphql(request: ServerRequest): Mono<ServerResponse> {
        @Suppress("UNCHECKED_CAST") val executionResult: Mono<ExecutionResult> =

            request.bodyToMono(String::class.java)
                .map {
                    if ("application/graphql" == request.headers().firstHeader("Content-Type")) {
                        QueryInput(it)
                    } else {
                        val readValue = mapper.readValue<Map<String, Any>>(it)
                        QueryInput(
                            readValue["query"] as String,

                            (readValue["variables"] ?: emptyMap<String, Any>()) as Map<String, Any>,
                            (readValue["extensions"] ?: emptyMap<String, Any>()) as Map<String, Any>,
                        )
                    }
                }
                .flatMap { queryInput ->
                    logger.debug("Parsed variables: {}", queryInput.queryVariables)

                    dgsQueryExecutor.execute(
                        queryInput.query,
                        queryInput.queryVariables,
                        queryInput.extensions,
                        request.headers().asHttpHeaders(),
                        "",
                        request
                    )
                }

        return executionResult.flatMap { result ->
            val graphQlOutput = result.toSpecification()
            ServerResponse.ok().bodyValue(graphQlOutput)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DgsWebfluxHttpHandler::class.java)
        private val mapper = jacksonObjectMapper()
    }
}

private data class QueryInput(
    val query: String,
    val queryVariables: Map<String, Any> = emptyMap(),
    val extensions: Map<String, Any> = emptyMap()
)
