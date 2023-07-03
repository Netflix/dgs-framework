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

package com.netflix.graphql.dgs.reactive.internal

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.mapper.MappingException
import com.netflix.graphql.dgs.exceptions.DgsQueryExecutionDataExtractionException
import com.netflix.graphql.dgs.exceptions.QueryException
import com.netflix.graphql.dgs.internal.BaseDgsQueryExecutor
import com.netflix.graphql.dgs.internal.DefaultDgsQueryExecutor
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.QueryValueCustomizer
import graphql.ExecutionResult
import graphql.execution.ExecutionIdProvider
import graphql.execution.ExecutionStrategy
import graphql.execution.NonNullableFieldWasNullError
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.preparsed.PreparsedDocumentProvider
import graphql.schema.GraphQLSchema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.*
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class DefaultDgsReactiveQueryExecutor(
    defaultSchema: GraphQLSchema,
    private val schemaProvider: DgsSchemaProvider,
    private val dataLoaderProvider: DgsDataLoaderProvider,
    private val contextBuilder: DefaultDgsReactiveGraphQLContextBuilder,
    private val instrumentation: Instrumentation?,
    private val queryExecutionStrategy: ExecutionStrategy,
    private val mutationExecutionStrategy: ExecutionStrategy,
    private val idProvider: Optional<ExecutionIdProvider>,
    private val reloadIndicator: DefaultDgsQueryExecutor.ReloadSchemaIndicator = DefaultDgsQueryExecutor.ReloadSchemaIndicator { false },
    private val preparsedDocumentProvider: PreparsedDocumentProvider? = null,
    private val queryValueCustomizer: QueryValueCustomizer = QueryValueCustomizer { query -> query }
) : com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor {

    private val schema = AtomicReference(defaultSchema)

    override fun execute(
        query: String?,
        variables: Map<String, Any>?,
        extensions: Map<String, Any>?,
        headers: HttpHeaders?,
        operationName: String?,
        serverHttpRequest: ServerRequest?
    ): Mono<ExecutionResult> {
        return Mono
            .fromCallable {
                if (reloadIndicator.reloadSchema()) {
                    schema.updateAndGet { schemaProvider.schema() }
                } else {
                    schema.get()
                }
            }
            .zipWith(contextBuilder.build(DgsReactiveRequestData(extensions, headers, serverHttpRequest)))
            .flatMap { (gqlSchema, dgsContext) ->
                Mono.fromCompletionStage(
                    BaseDgsQueryExecutor.baseExecute(
                        query = queryValueCustomizer.apply(query),
                        variables = variables,
                        extensions = extensions,
                        operationName = operationName,
                        dgsContext = dgsContext,
                        graphQLSchema = gqlSchema,
                        dataLoaderProvider = dataLoaderProvider,
                        instrumentation = instrumentation,
                        queryExecutionStrategy = queryExecutionStrategy,
                        mutationExecutionStrategy = mutationExecutionStrategy,
                        idProvider = idProvider,
                        preparsedDocumentProvider = preparsedDocumentProvider
                    )
                ).doOnEach { result ->
                    if (result.hasValue()) {
                        val nullValueError = result.get()?.errors?.find { it is NonNullableFieldWasNullError }
                        if (nullValueError != null) {
                            logger.error(nullValueError.message)
                        }
                    }
                }
            }
    }

    override fun <T : Any> executeAndExtractJsonPath(
        query: String,
        jsonPath: String,
        variables: Map<String, Any>?,
        serverRequest: ServerRequest?
    ): Mono<T> {
        return getJsonResult(query, variables, serverRequest).map { JsonPath.read(it, jsonPath) }
    }

    override fun executeAndGetDocumentContext(
        query: String,
        variables: Map<String, Any>
    ): Mono<DocumentContext> {
        return getJsonResult(query, variables, null).map(BaseDgsQueryExecutor.parseContext::parse)
    }

    override fun <T : Any?> executeAndExtractJsonPathAsObject(
        query: String,
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
        query: String,
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

    private fun getJsonResult(query: String, variables: Map<String, Any>?, serverRequest: ServerRequest?): Mono<String> {
        val httpHeaders = serverRequest?.headers()?.asHttpHeaders()
        return execute(query, variables, null, httpHeaders, null, serverRequest).map { executionResult ->
            if (executionResult.errors.size > 0) {
                throw QueryException(executionResult.errors)
            }

            BaseDgsQueryExecutor.objectMapper.writeValueAsString(executionResult.toSpecification())
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DefaultDgsQueryExecutor::class.java)
    }
}
