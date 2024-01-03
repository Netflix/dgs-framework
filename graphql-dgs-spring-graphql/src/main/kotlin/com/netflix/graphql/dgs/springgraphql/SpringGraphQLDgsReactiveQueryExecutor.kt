/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.graphql.dgs.springgraphql

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.mapper.MappingException
import com.netflix.graphql.dgs.exceptions.DgsQueryExecutionDataExtractionException
import com.netflix.graphql.dgs.exceptions.QueryException
import com.netflix.graphql.dgs.internal.BaseDgsQueryExecutor
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveGraphQLContextBuilder
import com.netflix.graphql.dgs.reactive.internal.DgsReactiveRequestData
import graphql.ExecutionResult
import graphql.GraphQLContext
import graphql.GraphQLError
import org.intellij.lang.annotations.Language
import org.springframework.graphql.execution.DefaultExecutionGraphQlService
import org.springframework.graphql.support.DefaultExecutionGraphQlRequest
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture

class SpringGraphQLDgsReactiveQueryExecutor(
    val executionService: DefaultExecutionGraphQlService,
    private val dgsContextBuilder: DefaultDgsReactiveGraphQLContextBuilder,
    private val dgsDataLoaderProvider: DgsDataLoaderProvider
) : DgsReactiveQueryExecutor {
    override fun execute(
        query: String?,
        variables: Map<String, Any>?,
        extensions: Map<String, Any>?,
        headers: HttpHeaders?,
        operationName: String?,
        serverRequest: org.springframework.web.reactive.function.server.ServerRequest?
    ): Mono<ExecutionResult> {
        val request = DefaultExecutionGraphQlRequest(
            query,
            operationName,
            variables,
            extensions,
            "",
            null
        )

        val graphQLContextFuture = CompletableFuture<GraphQLContext>()
        val dataLoaderRegistry = dgsDataLoaderProvider.buildRegistryWithContextSupplier { graphQLContextFuture.get() }
        return dgsContextBuilder.build(DgsReactiveRequestData(request.extensions, headers, serverRequest))
            .flatMap { context ->
                request.configureExecutionInput { _, builder ->
                    builder
                        .context(context)
                        .graphQLContext(context)
                        .dataLoaderRegistry(dataLoaderRegistry).build()
                }

                graphQLContextFuture.complete(request.toExecutionInput().graphQLContext)

                executionService.execute(
                    request
                ) ?: throw IllegalStateException("Unexpected null response from Spring GraphQL client")
            }.map { execute ->
                ExecutionResult.newExecutionResult()
                    .data(execute.getData())
                    .errors(execute.errors.map { GraphQLError.newError().message(it.message).build() })
                    .build()
            }
    }

    override fun <T : Any> executeAndExtractJsonPath(
        @Language("graphql") query: String,
        jsonPath: String,
        variables: Map<String, Any>?,
        serverRequest: ServerRequest?
    ): Mono<T> {
        return getJsonResult(query, variables, serverRequest).map { JsonPath.read(it, jsonPath) }
    }

    override fun executeAndGetDocumentContext(
        @Language("graphql") query: String,
        variables: Map<String, Any>
    ): Mono<DocumentContext> {
        return getJsonResult(query, variables, null).map(BaseDgsQueryExecutor.parseContext::parse)
    }

    override fun <T : Any?> executeAndExtractJsonPathAsObject(
        @Language("graphql") query: String,
        jsonPath: String,
        variables: Map<String, Any>,
        clazz: Class<T>
    ): Mono<T> {
        return getJsonResult(query, variables, null)
            .map(BaseDgsQueryExecutor.parseContext::parse)
            .map {
                try {
                    it.read(jsonPath, clazz)
                } catch (ex: MappingException) {
                    throw DgsQueryExecutionDataExtractionException(ex, it.jsonString(), jsonPath, clazz)
                }
            }
    }

    override fun <T : Any?> executeAndExtractJsonPathAsObject(
        @Language("graphql") query: String,
        jsonPath: String,
        variables: Map<String, Any>,
        typeRef: TypeRef<T>
    ): Mono<T> {
        return getJsonResult(query, variables, null)
            .map(BaseDgsQueryExecutor.parseContext::parse)
            .map {
                try {
                    it.read(jsonPath, typeRef)
                } catch (ex: MappingException) {
                    throw DgsQueryExecutionDataExtractionException(ex, it.jsonString(), jsonPath, typeRef)
                }
            }
    }

    private fun getJsonResult(@Language("graphql") query: String, variables: Map<String, Any>?, serverRequest: ServerRequest?): Mono<String> {
        val httpHeaders = serverRequest?.headers()?.asHttpHeaders()
        return execute(query, variables, null, httpHeaders, null, serverRequest).map { executionResult ->
            if (executionResult.errors.size > 0) {
                throw QueryException(executionResult.errors)
            }

            BaseDgsQueryExecutor.objectMapper.writeValueAsString(executionResult.toSpecification())
        }
    }
}
