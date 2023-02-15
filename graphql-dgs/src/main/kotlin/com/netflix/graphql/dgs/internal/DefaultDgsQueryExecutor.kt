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

package com.netflix.graphql.dgs.internal

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.mapper.MappingException
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.exceptions.DgsQueryExecutionDataExtractionException
import com.netflix.graphql.dgs.exceptions.QueryException
import com.netflix.graphql.dgs.internal.BaseDgsQueryExecutor.parseContext
import com.netflix.graphql.dgs.internal.DefaultDgsQueryExecutor.ReloadSchemaIndicator
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
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Main Query executing functionality. This should be reused between different transport protocols and the testing framework.
 */
class DefaultDgsQueryExecutor(
    defaultSchema: GraphQLSchema,
    private val schemaProvider: DgsSchemaProvider,
    private val dataLoaderProvider: DgsDataLoaderProvider,
    private val contextBuilder: DefaultDgsGraphQLContextBuilder,
    private val instrumentation: Instrumentation?,
    private val queryExecutionStrategy: ExecutionStrategy,
    private val mutationExecutionStrategy: ExecutionStrategy,
    private val idProvider: Optional<ExecutionIdProvider>,
    private val reloadIndicator: ReloadSchemaIndicator = ReloadSchemaIndicator { false },
    private val preparsedDocumentProvider: PreparsedDocumentProvider? = null,
    private val queryValueCustomizer: QueryValueCustomizer = QueryValueCustomizer { query -> query },
    private val requestCustomizer: DgsQueryExecutorRequestCustomizer = DgsQueryExecutorRequestCustomizer.DEFAULT_REQUEST_CUSTOMIZER
) : DgsQueryExecutor {

    val schema = AtomicReference(defaultSchema)

    override fun execute(
        query: String?,
        variables: Map<String, Any>,
        extensions: Map<String, Any>?,
        headers: HttpHeaders?,
        operationName: String?,
        webRequest: WebRequest?
    ): ExecutionResult {
        val graphQLSchema: GraphQLSchema =
            if (reloadIndicator.reloadSchema()) {
                schema.updateAndGet { schemaProvider.schema() }
            } else {
                schema.get()
            }

        val request = requestCustomizer.apply(webRequest ?: RequestContextHolder.getRequestAttributes() as? WebRequest, headers)
        val dgsContext = contextBuilder.build(DgsWebMvcRequestData(extensions, headers, request))

        val executionResult =
            BaseDgsQueryExecutor.baseExecute(
                query = queryValueCustomizer.apply(query),
                variables = variables,
                extensions = extensions,
                operationName = operationName,
                dgsContext = dgsContext,
                graphQLSchema = graphQLSchema,
                dataLoaderProvider = dataLoaderProvider,
                instrumentation = instrumentation,
                queryExecutionStrategy = queryExecutionStrategy,
                mutationExecutionStrategy = mutationExecutionStrategy,
                idProvider = idProvider,
                preparsedDocumentProvider = preparsedDocumentProvider
            )

        // Check for NonNullableFieldWasNull errors, and log them explicitly because they don't run through the exception handlers.
        val result = executionResult.get()
        if (result.errors.size > 0) {
            val nullValueError = result.errors.find { it is NonNullableFieldWasNullError }
            if (nullValueError != null) {
                logger.error(nullValueError.message)
            }
        }

        return result
    }

    override fun <T> executeAndExtractJsonPath(query: String, jsonPath: String, variables: Map<String, Any>): T {
        return JsonPath.read(getJsonResult(query, variables), jsonPath)
    }

    override fun <T : Any?> executeAndExtractJsonPath(query: String, jsonPath: String, headers: HttpHeaders): T {
        return JsonPath.read(getJsonResult(query, emptyMap(), headers), jsonPath)
    }

    override fun <T : Any?> executeAndExtractJsonPath(query: String, jsonPath: String, servletWebRequest: ServletWebRequest): T {
        val httpHeaders = HttpHeaders()
        servletWebRequest.headerNames.forEach { name ->
            httpHeaders.addAll(name, servletWebRequest.getHeaderValues(name).orEmpty().toList())
        }
        return JsonPath.read(getJsonResult(query, emptyMap(), httpHeaders, servletWebRequest), jsonPath)
    }

    override fun <T> executeAndExtractJsonPathAsObject(
        query: String,
        jsonPath: String,
        variables: Map<String, Any>,
        clazz: Class<T>,
        headers: HttpHeaders?
    ): T {
        val jsonResult = getJsonResult(query, variables, headers)
        return try {
            parseContext.parse(jsonResult).read(jsonPath, clazz)
        } catch (ex: MappingException) {
            throw DgsQueryExecutionDataExtractionException(ex, jsonResult, jsonPath, clazz)
        }
    }

    override fun <T> executeAndExtractJsonPathAsObject(
        query: String,
        jsonPath: String,
        variables: Map<String, Any>,
        typeRef: TypeRef<T>,
        headers: HttpHeaders?
    ): T {
        val jsonResult = getJsonResult(query, variables, headers)
        return try {
            parseContext.parse(jsonResult).read(jsonPath, typeRef)
        } catch (ex: MappingException) {
            throw DgsQueryExecutionDataExtractionException(ex, jsonResult, jsonPath, typeRef)
        }
    }

    override fun executeAndGetDocumentContext(query: String, variables: Map<String, Any>): DocumentContext {
        return parseContext.parse(getJsonResult(query, variables))
    }

    override fun executeAndGetDocumentContext(
        query: String,
        variables: MutableMap<String, Any>,
        headers: HttpHeaders?
    ): DocumentContext {
        return parseContext.parse(getJsonResult(query, variables, headers))
    }

    private fun getJsonResult(query: String, variables: Map<String, Any>, headers: HttpHeaders? = null, servletWebRequest: ServletWebRequest? = null): String {
        val executionResult = execute(query, variables, null, headers, null, servletWebRequest)

        if (executionResult.errors.size > 0) {
            throw QueryException(executionResult.errors)
        }

        return BaseDgsQueryExecutor.objectMapper.writeValueAsString(executionResult.toSpecification())
    }

    /**
     * Provides the means to identify if executor should reload the [GraphQLSchema] from the given [DgsSchemaProvider].
     * If `true` the schema will be reloaded, else the default schema, provided in the cunstructor of the [DefaultDgsQueryExecutor],
     * will be used.
     *
     * @implSpec The implementation should be thread-safe.
     */
    @FunctionalInterface
    fun interface ReloadSchemaIndicator {
        fun reloadSchema(): Boolean
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DefaultDgsQueryExecutor::class.java)
    }
}
