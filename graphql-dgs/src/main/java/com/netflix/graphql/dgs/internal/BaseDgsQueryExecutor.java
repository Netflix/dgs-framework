/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.graphql.dgs.internal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.netflix.graphql.dgs.DgsExecutionResult;
import com.netflix.graphql.dgs.context.DgsContext;
import com.netflix.graphql.dgs.exceptions.DgsBadRequestException;
import com.netflix.graphql.types.errors.TypedGraphQLError;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.execution.ExecutionIdProvider;
import graphql.execution.ExecutionStrategy;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.schema.GraphQLSchema;
import org.dataloader.DataLoaderRegistry;
import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

public final class BaseDgsQueryExecutor {
    private static final Logger logger = LoggerFactory.getLogger(BaseDgsQueryExecutor.class);

    private BaseDgsQueryExecutor() {
    }

    /** Lazily initialized Jackson 2 support, which is an optional dependency. */
    private static final class Jackson2Holder {
        private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
        private static final ParseContext PARSE_CONTEXT = createParseContext();

        private static ObjectMapper createObjectMapper() {
            try {
                return new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            } catch (NoClassDefFoundError e) {
                throw new IllegalStateException(
                        "BaseDgsQueryExecutor.objectMapper requires Jackson 2 on the classpath. "
                                + "Add the graphql-dgs-jackson2 module or use DgsJsonMapper instead.",
                        e);
            }
        }

        private static ParseContext createParseContext() {
            try {
                return JsonPath.using(Configuration
                        .builder()
                        .jsonProvider(new JacksonJsonProvider(OBJECT_MAPPER))
                        .mappingProvider(new JacksonMappingProvider(OBJECT_MAPPER))
                        .build()
                        .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL));
            } catch (NoClassDefFoundError e) {
                throw new IllegalStateException(
                        "BaseDgsQueryExecutor.parseContext requires Jackson 2 on the classpath. "
                                + "Add the graphql-dgs-jackson2 module or use DgsJsonMapper instead.",
                        e);
            }
        }
    }

    /**
     * @deprecated Use DgsJsonMapper instead. This field requires Jackson 2 on the classpath.
     */
    @Deprecated
    public static ObjectMapper getObjectMapper() {
        return Jackson2Holder.OBJECT_MAPPER;
    }

    /**
     * @deprecated Use DgsJsonMapper.jsonPathConfiguration() instead. This field requires Jackson 2 on the classpath.
     */
    @Deprecated
    public static ParseContext getParseContext() {
        return Jackson2Holder.PARSE_CONTEXT;
    }

    @SuppressWarnings("deprecation")
    public static CompletableFuture<ExecutionResult> baseExecute(
            @Language("graphql") String query,
            Map<String, Object> variables,
            Map<String, Object> extensions,
            String operationName,
            DgsContext dgsContext,
            GraphQLSchema graphQLSchema,
            DgsDataLoaderProvider dataLoaderProvider,
            Instrumentation instrumentation,
            ExecutionStrategy queryExecutionStrategy,
            ExecutionStrategy mutationExecutionStrategy,
            Optional<ExecutionIdProvider> idProvider,
            PreparsedDocumentProvider preparsedDocumentProvider) {
        Map<String, Object> inputVariables = variables != null ? variables : Map.of();

        if (!StringUtils.hasText(query)) {
            return CompletableFuture.completedFuture(DgsExecutionResult
                    .builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .executionResult(ExecutionResult
                            .newExecutionResult()
                            .addError(DgsBadRequestException.NULL_OR_EMPTY_QUERY_EXCEPTION.toGraphQlError())
                            .build())
                    .build());
        }

        GraphQL.Builder graphQLBuilder = GraphQL
                .newGraphQL(graphQLSchema)
                .queryExecutionStrategy(queryExecutionStrategy)
                .mutationExecutionStrategy(mutationExecutionStrategy);

        if (preparsedDocumentProvider != null) {
            graphQLBuilder.preparsedDocumentProvider(preparsedDocumentProvider);
        }
        if (instrumentation != null) {
            graphQLBuilder.instrumentation(instrumentation);
        }
        idProvider.ifPresent(graphQLBuilder::executionIdProvider);

        GraphQL graphQL = graphQLBuilder.build();

        AtomicReference<ExecutionInput> executionInputRef = new AtomicReference<>();
        DataLoaderRegistry dataLoaderRegistry = dataLoaderProvider.buildRegistryWithContextSupplier(
                () -> executionInputRef.get().getGraphQLContext());

        ExecutionInput executionInput = ExecutionInput
                .newExecutionInput()
                .query(query)
                .operationName(operationName)
                .variables(inputVariables)
                .dataLoaderRegistry(dataLoaderRegistry)
                .context(dgsContext)
                .graphQLContext(dgsContext)
                .extensions(extensions != null ? extensions : Map.of())
                .build();
        executionInputRef.set(executionInput);

        try {
            CompletableFuture<ExecutionResult> future = graphQL.executeAsync(executionInput);

            if (dataLoaderRegistry instanceof AutoCloseable closeableRegistry) {
                future.whenComplete((result, exception) -> {
                    try {
                        closeableRegistry.close();
                    } catch (Exception e) {
                        logger.warn("Error closing data loader registry", e);
                    }
                });
            }

            return future.exceptionally(exc -> {
                Throwable cause = exc instanceof CompletionException ? exc.getCause() : exc;
                if (cause instanceof GraphQLError graphQLError) {
                    return DgsExecutionResult
                            .builder()
                            .status(HttpStatus.BAD_REQUEST)
                            .executionResult(
                                    ExecutionResult.newExecutionResult().addError(graphQLError).build())
                            .build();
                }
                logger.error("Encountered an exception while handling query {}", query, cause);
                return DgsExecutionResult
                        .builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .executionResult(ExecutionResult
                                .newExecutionResult()
                                .addError(TypedGraphQLError.newInternalErrorBuilder().build())
                                .build())
                        .build();
            });
        } catch (Exception e) {
            logger.error("Encountered an exception while handling query {}", query, e);
            ExecutionResult.Builder<?> executionResult = ExecutionResult.newExecutionResult();
            if (e instanceof GraphQLError graphQLError) {
                executionResult.addError(graphQLError);
            } else {
                executionResult.addError(TypedGraphQLError.newInternalErrorBuilder().build());
            }
            return CompletableFuture.completedFuture(executionResult.build());
        }
    }
}
