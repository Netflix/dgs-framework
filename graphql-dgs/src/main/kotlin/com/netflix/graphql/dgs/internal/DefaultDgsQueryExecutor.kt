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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jayway.jsonpath.*
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import com.jayway.jsonpath.spi.mapper.MappingException
import com.netflix.graphql.dgs.DgsContextBuilder
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.exceptions.DgsQueryExecutionDataExtractionException
import com.netflix.graphql.dgs.exceptions.QueryException
import com.netflix.graphql.dgs.internal.DefaultDgsQueryExecutor.ReloadSchemaIndicator
import graphql.*
import graphql.execution.ExecutionIdProvider
import graphql.execution.ExecutionStrategy
import graphql.execution.NonNullableFieldWasNullError
import graphql.execution.SubscriptionExecutionStrategy
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.schema.GraphQLSchema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
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
    private val contextBuilder: DgsContextBuilder,
    private val chainedInstrumentation: ChainedInstrumentation,
    private val queryExecutionStrategy: ExecutionStrategy,
    private val mutationExecutionStrategy: ExecutionStrategy,
    private val idProvider: Optional<ExecutionIdProvider>,
    private val reloadIndicator: ReloadSchemaIndicator = ReloadSchemaIndicator { false }
) : DgsQueryExecutor {

    private val parseContext: ParseContext =
        JsonPath.using(
            Configuration.builder()
                .jsonProvider(JacksonJsonProvider(jacksonObjectMapper()))
                .mappingProvider(
                    JacksonMappingProvider(
                        jacksonObjectMapper()
                            .registerModule(JavaTimeModule())
                            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    )
                ).build()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)
        )

    val logger: Logger = LoggerFactory.getLogger(DefaultDgsQueryExecutor::class.java)

    val schema = AtomicReference(defaultSchema)

    override fun execute(
        query: String,
        variables: Map<String, Any>,
        extensions: Map<String, Any>?,
        headers: HttpHeaders?,
        operationName: String?,
        webRequest: WebRequest?
    ): ExecutionResult {
        val graphQLSchema: GraphQLSchema =
            if (reloadIndicator.reloadSchema())
                schema.updateAndGet { schemaProvider.schema() }
            else
                schema.get()

        val graphQLBuilder =
            GraphQL.newGraphQL(graphQLSchema)
                .instrumentation(chainedInstrumentation)
                .queryExecutionStrategy(queryExecutionStrategy)
                .mutationExecutionStrategy(mutationExecutionStrategy)
                .subscriptionExecutionStrategy(SubscriptionExecutionStrategy())
        if (idProvider.isPresent) {
            graphQLBuilder.executionIdProvider(idProvider.get())
        }
        val graphQL = graphQLBuilder.build()

        val dgsContext = contextBuilder.build(DgsRequestData(extensions, headers, webRequest))
        val dataLoaderRegistry = dataLoaderProvider.buildRegistryWithContextSupplier({ dgsContext })
        val executionInput: ExecutionInput = ExecutionInput.newExecutionInput()
            .query(query)
            .dataLoaderRegistry(dataLoaderRegistry)
            .variables(variables)
            .operationName(operationName)
            .context(dgsContext)
            .build()

        val executionResult = try {
            graphQL.execute(executionInput)
        } catch (e: Exception) {
            logger.error("Encountered an exception while handling query $query", e)
            val errors: List<GraphQLError> = if (e is GraphQLError) listOf(e) else emptyList()
            ExecutionResultImpl(null, errors)
        }

        // Check for NonNullableFieldWasNull errors, and log them explicitly because they don't run through the exception handlers.
        if (executionResult.errors.size > 0) {
            val nullValueError = executionResult.errors.find { it is NonNullableFieldWasNullError }
            if (nullValueError != null) {
                logger.error(nullValueError.message)
            }
        }

        return executionResult
    }

    override fun execute(query: String, variables: Map<String, Any>, operationName: String?): ExecutionResult {
        return execute(
            query = query,
            variables = variables,
            operationName = operationName,
            extensions = null,
            headers = null,
            webRequest = null
        )
    }

    override fun <T> executeAndExtractJsonPath(query: String, jsonPath: String): T {
        return JsonPath.read(getJsonResult(query, emptyMap()), jsonPath)
    }

    override fun <T> executeAndExtractJsonPath(query: String, jsonPath: String, variables: Map<String, Any>): T {
        return JsonPath.read(getJsonResult(query, variables), jsonPath)
    }

    override fun <T> executeAndExtractJsonPathAsObject(query: String, jsonPath: String, clazz: Class<T>): T {
        val jsonResult = getJsonResult(query, emptyMap())
        return try {
            parseContext.parse(jsonResult).read(jsonPath, clazz)
        } catch (ex: MappingException) {
            throw DgsQueryExecutionDataExtractionException(ex, jsonResult, jsonPath, clazz)
        }
    }

    override fun <T> executeAndExtractJsonPathAsObject(query: String, jsonPath: String, variables: Map<String, Any>, clazz: Class<T>): T {
        val jsonResult = getJsonResult(query, variables)
        return try {
            parseContext.parse(jsonResult).read(jsonPath, clazz)
        } catch (ex: MappingException) {
            throw DgsQueryExecutionDataExtractionException(ex, jsonResult, jsonPath, clazz)
        }
    }

    override fun <T> executeAndExtractJsonPathAsObject(query: String, jsonPath: String, typeRef: TypeRef<T>): T {
        val jsonResult = getJsonResult(query, emptyMap())
        return try {
            parseContext.parse(jsonResult).read(jsonPath, typeRef)
        } catch (ex: MappingException) {
            throw DgsQueryExecutionDataExtractionException(ex, jsonResult, jsonPath, typeRef)
        }
    }

    override fun <T> executeAndExtractJsonPathAsObject(query: String, jsonPath: String, variables: Map<String, Any>, typeRef: TypeRef<T>): T {
        val jsonResult = getJsonResult(query, variables)
        return try {
            parseContext.parse(jsonResult).read(jsonPath, typeRef)
        } catch (ex: MappingException) {
            throw DgsQueryExecutionDataExtractionException(ex, jsonResult, jsonPath, typeRef)
        }
    }

    override fun executeAndGetDocumentContext(query: String): DocumentContext {
        return parseContext.parse(getJsonResult(query, emptyMap()))
    }

    override fun executeAndGetDocumentContext(query: String, variables: Map<String, Any>): DocumentContext {
        return parseContext.parse(getJsonResult(query, variables))
    }

    private fun getJsonResult(query: String, variables: Map<String, Any>): String {
        val executionResult = execute(query, variables)

        if (executionResult.errors.size > 0) {
            throw QueryException(executionResult.errors)
        }

        val objectMapper = ObjectMapper()
        return objectMapper.writeValueAsString(executionResult.toSpecification())
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
}
