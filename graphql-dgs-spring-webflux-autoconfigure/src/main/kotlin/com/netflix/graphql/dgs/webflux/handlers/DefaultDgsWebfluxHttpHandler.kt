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

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import graphql.ExecutionResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

class DefaultDgsWebfluxHttpHandler(private val dgsQueryExecutor: DgsReactiveQueryExecutor) : DgsWebfluxHttpHandler {

    override fun graphql(request: ServerRequest): Mono<ServerResponse> {
        @Suppress("UNCHECKED_CAST") val executionResult: Mono<ExecutionResult> =

            request.bodyToMono(String::class.java)
                .flatMap { body ->
                    if ("application/graphql" == request.headers().firstHeader("Content-Type")) {
                        Mono.just(QueryInput(body))
                    } else {
                        Mono.fromCallable {
                            val readValue = mapper.readValue<Map<String, Any>>(body)
                            val query: String? = when (val iq = readValue["query"]) {
                                is String -> iq
                                else -> null
                            }
                            QueryInput(
                                query,
                                (readValue["variables"] ?: emptyMap<String, Any>()) as Map<String, Any>,
                                (readValue["extensions"] ?: emptyMap<String, Any>()) as Map<String, Any>,
                            )
                        }
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
            ServerResponse
                .ok()
                .bodyValue(result.toSpecification())
        }.onErrorResume { ex ->
            when (ex) {
                is JsonParseException ->
                    ServerResponse.badRequest()
                        .bodyValue("Invalid query - ${ex.message ?: "no details found in the error message"}.")
                is MismatchedInputException ->
                    ServerResponse.badRequest()
                        .bodyValue("Invalid query - No content to map to input.")
                else ->
                    ServerResponse.badRequest()
                        .bodyValue("Invalid query - ${ex.message ?: "no additional details found"}.")
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DefaultDgsWebfluxHttpHandler::class.java)
        private val mapper = jacksonObjectMapper()
    }
}

private data class QueryInput(
    val query: String?,
    val queryVariables: Map<String, Any> = emptyMap(),
    val extensions: Map<String, Any> = emptyMap()
)
