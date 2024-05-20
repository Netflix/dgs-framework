/*
 * Copyright 2022 Netflix, Inc.
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
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.ParseContext
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import com.netflix.graphql.dgs.DgsExecutionResult
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.exceptions.DgsBadRequestException
import com.netflix.graphql.types.errors.TypedGraphQLError
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLError
import graphql.execution.ExecutionIdProvider
import graphql.execution.ExecutionStrategy
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.preparsed.PreparsedDocumentProvider
import graphql.schema.GraphQLSchema
import org.intellij.lang.annotations.Language
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.util.StringUtils
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

object BaseDgsQueryExecutor {

    private val logger: Logger = LoggerFactory.getLogger(BaseDgsQueryExecutor::class.java)

    val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    val parseContext: ParseContext =
        JsonPath.using(
            Configuration.builder()
                .jsonProvider(JacksonJsonProvider(jacksonObjectMapper()))
                .mappingProvider(JacksonMappingProvider(objectMapper)).build()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)
        )

    fun baseExecute(
        @Language("graphql") query: String?,
        variables: Map<String, Any>?,
        extensions: Map<String, Any>?,
        operationName: String?,
        dgsContext: DgsContext,
        graphQLSchema: GraphQLSchema,
        dataLoaderProvider: DgsDataLoaderProvider,
        instrumentation: Instrumentation?,
        queryExecutionStrategy: ExecutionStrategy,
        mutationExecutionStrategy: ExecutionStrategy,
        idProvider: Optional<ExecutionIdProvider>,
        preparsedDocumentProvider: PreparsedDocumentProvider?
    ): CompletableFuture<ExecutionResult> {
        val inputVariables = variables ?: emptyMap()

        if (!StringUtils.hasText(query)) {
            return CompletableFuture.completedFuture(
                DgsExecutionResult
                    .builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .executionResult(
                        ExecutionResult.newExecutionResult()
                            .addError(DgsBadRequestException.NULL_OR_EMPTY_QUERY_EXCEPTION.toGraphQlError())
                            .build()
                    ).build()
            )
        }

        val graphQLBuilder =
            GraphQL.newGraphQL(graphQLSchema)
                .queryExecutionStrategy(queryExecutionStrategy)
                .mutationExecutionStrategy(mutationExecutionStrategy)

        preparsedDocumentProvider?.let { graphQLBuilder.preparsedDocumentProvider(it) }
        instrumentation?.let { graphQLBuilder.instrumentation(it) }
        idProvider.ifPresent { graphQLBuilder.executionIdProvider(it) }

        val graphQL: GraphQL = graphQLBuilder.build()

        lateinit var executionInput: ExecutionInput
        val dataLoaderRegistry = dataLoaderProvider.buildRegistryWithContextSupplier { executionInput.graphQLContext }

        @Suppress("DEPRECATION")
        executionInput = ExecutionInput.newExecutionInput()
            .query(query)
            .operationName(operationName)
            .variables(inputVariables)
            .dataLoaderRegistry(dataLoaderRegistry)
            .context(dgsContext)
            .graphQLContext(dgsContext)
            .extensions(extensions.orEmpty())
            .build()

        return try {
            val future = graphQL.executeAsync(executionInput)

            if (dataLoaderRegistry is AutoCloseable) {
                future.whenComplete { _, _ -> dataLoaderRegistry.close() }
            }

            future.exceptionally { exc ->
                val cause = if (exc is CompletionException) {
                    exc.cause
                } else {
                    exc
                }
                if (cause is GraphQLError) {
                    DgsExecutionResult
                        .builder()
                        .status(HttpStatus.BAD_REQUEST)
                        .executionResult(ExecutionResult.newExecutionResult().addError(cause).build())
                        .build()
                } else {
                    logger.error("Encountered an exception while handling query {}", query, cause)
                    DgsExecutionResult
                        .builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .executionResult(
                            ExecutionResult.newExecutionResult().addError(
                                TypedGraphQLError.newInternalErrorBuilder().build()
                            ).build()
                        )
                        .build()
                }
            }
        } catch (e: Exception) {
            logger.error("Encountered an exception while handling query {}", query, e)
            val executionResult = ExecutionResult.newExecutionResult()
            if (e is GraphQLError) {
                executionResult.addError(e)
            } else {
                executionResult.addError(TypedGraphQLError.newInternalErrorBuilder().build())
            }
            CompletableFuture.completedFuture(executionResult.build())
        }
    }
}
