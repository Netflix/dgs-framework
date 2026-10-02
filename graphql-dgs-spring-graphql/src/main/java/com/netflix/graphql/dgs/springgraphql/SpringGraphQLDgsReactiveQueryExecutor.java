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

package com.netflix.graphql.dgs.springgraphql;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.mapper.MappingException;
import com.netflix.graphql.dgs.exceptions.DgsQueryExecutionDataExtractionException;
import com.netflix.graphql.dgs.exceptions.QueryException;
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider;
import com.netflix.graphql.dgs.json.DgsJsonMapper;
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor;
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveGraphQLContextBuilder;
import com.netflix.graphql.dgs.reactive.internal.DgsReactiveRequestData;
import graphql.ExecutionResult;
import graphql.GraphQLContext;
import org.dataloader.DataLoaderRegistry;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.support.DefaultExecutionGraphQlRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class SpringGraphQLDgsReactiveQueryExecutor implements DgsReactiveQueryExecutor {
    private final ExecutionGraphQlService executionService;
    private final DefaultDgsReactiveGraphQLContextBuilder dgsContextBuilder;
    private final DgsDataLoaderProvider dgsDataLoaderProvider;
    private final DgsJsonMapper dgsJsonMapper;
    private final ParseContext parseContext;

    public SpringGraphQLDgsReactiveQueryExecutor(
            @NotNull ExecutionGraphQlService executionService,
            @NotNull DefaultDgsReactiveGraphQLContextBuilder dgsContextBuilder,
            @NotNull DgsDataLoaderProvider dgsDataLoaderProvider,
            @NotNull DgsJsonMapper dgsJsonMapper) {
        this.executionService = executionService;
        this.dgsContextBuilder = dgsContextBuilder;
        this.dgsDataLoaderProvider = dgsDataLoaderProvider;
        this.dgsJsonMapper = dgsJsonMapper;
        this.parseContext = JsonPath.using(dgsJsonMapper.jsonPathConfiguration());
    }

    /**
     * Accepts typed variable and extension maps, as the original Kotlin implementation did.
     * CharSequence avoids an erasure clash with the invariant maps required by DgsReactiveQueryExecutor.
     */
    @NotNull
    public Mono<ExecutionResult> execute(
            @NotNull @Language("graphql") CharSequence query,
            @Nullable Map<String, ? extends Object> variables,
            @Nullable Map<String, ? extends Object> extensions,
            @Nullable HttpHeaders headers,
            @Nullable String operationName,
            @Nullable ServerRequest serverRequest) {
        return execute(query.toString(), castMap(variables), castMap(extensions), headers, operationName, serverRequest);
    }

    @NotNull
    @Override
    public Mono<ExecutionResult> execute(
            @NotNull @Language("graphql") String query,
            @Nullable Map<String, Object> variables,
            @Nullable Map<String, Object> extensions,
            @Nullable HttpHeaders headers,
            @Nullable String operationName,
            @Nullable ServerRequest serverRequest) {
        DefaultExecutionGraphQlRequest request =
                new DefaultExecutionGraphQlRequest(
                        query, operationName, castMap(variables), castMap(extensions), "", null);

        AtomicReference<GraphQLContext> graphQLContext = new AtomicReference<>();
        DataLoaderRegistry dataLoaderRegistry =
                dgsDataLoaderProvider.buildRegistryWithContextSupplier(graphQLContext::get);
        return dgsContextBuilder
                .build(new DgsReactiveRequestData(request.getExtensions(), headers, serverRequest))
                .flatMap(context -> {
                    request.configureExecutionInput((executionInput, builder) -> builder.graphQLContext(context)
                            .dataLoaderRegistry(dataLoaderRegistry)
                            .build());

                    graphQLContext.set(request.toExecutionInput().getGraphQLContext());

                    return executionService.execute(request);
                })
                .map(ExecutionGraphQlResponse::getExecutionResult);
    }

    /** Accepts typed variable maps without changing the executor interface's invariant signature. */
    @NotNull
    public <T> Mono<T> executeAndExtractJsonPath(
            @NotNull @Language("graphql") CharSequence query,
            @NotNull String jsonPath,
            @Nullable Map<String, ? extends Object> variables,
            @Nullable ServerRequest serverRequest) {
        return executeAndExtractJsonPath(query.toString(), jsonPath, castMap(variables), serverRequest);
    }

    @NotNull
    @Override
    public <T> Mono<T> executeAndExtractJsonPath(
            @NotNull @Language("graphql") String query,
            @NotNull String jsonPath,
            @Nullable Map<String, Object> variables,
            @Nullable ServerRequest serverRequest) {
        return getJsonResult(query, variables, serverRequest).map(json -> JsonPath.read(json, jsonPath));
    }

    /** Accepts typed variable maps without changing the executor interface's invariant signature. */
    @NotNull
    public Mono<DocumentContext> executeAndGetDocumentContext(
            @NotNull @Language("graphql") CharSequence query, @NotNull Map<String, ? extends Object> variables) {
        return executeAndGetDocumentContext(query.toString(), castMap(variables));
    }

    @NotNull
    @Override
    public Mono<DocumentContext> executeAndGetDocumentContext(
            @NotNull @Language("graphql") String query, @NotNull Map<String, Object> variables) {
        return getJsonResult(query, variables, null).map(parseContext::parse);
    }

    /** Accepts typed variable maps without changing the executor interface's invariant signature. */
    @NotNull
    public <T> Mono<T> executeAndExtractJsonPathAsObject(
            @NotNull @Language("graphql") CharSequence query, @NotNull String jsonPath,
            @NotNull Map<String, ? extends Object> variables, @NotNull Class<T> clazz) {
        return executeAndExtractJsonPathAsObject(query.toString(), jsonPath, castMap(variables), clazz);
    }

    @NotNull
    @Override
    public <T> Mono<T> executeAndExtractJsonPathAsObject(
            @NotNull @Language("graphql") String query, @NotNull String jsonPath, @NotNull Map<String, Object> variables, @NotNull Class<T> clazz) {
        return getJsonResult(query, variables, null).map(parseContext::parse).map(documentContext -> {
            try {
                return documentContext.read(jsonPath, clazz);
            } catch (MappingException ex) {
                throw new DgsQueryExecutionDataExtractionException(
                        ex, documentContext.jsonString(), jsonPath, clazz);
            }
        });
    }

    /** Accepts typed variable maps without changing the executor interface's invariant signature. */
    @NotNull
    public <T> Mono<T> executeAndExtractJsonPathAsObject(
            @NotNull @Language("graphql") CharSequence query, @NotNull String jsonPath,
            @NotNull Map<String, ? extends Object> variables, @NotNull TypeRef<T> typeRef) {
        return executeAndExtractJsonPathAsObject(query.toString(), jsonPath, castMap(variables), typeRef);
    }

    @NotNull
    @Override
    public <T> Mono<T> executeAndExtractJsonPathAsObject(
            @NotNull @Language("graphql") String query, @NotNull String jsonPath, @NotNull Map<String, Object> variables, @NotNull TypeRef<T> typeRef) {
        return getJsonResult(query, variables, null).map(parseContext::parse).map(documentContext -> {
            try {
                return documentContext.read(jsonPath, typeRef);
            } catch (MappingException ex) {
                throw new DgsQueryExecutionDataExtractionException(
                        ex, documentContext.jsonString(), jsonPath, typeRef);
            }
        });
    }

    private Mono<String> getJsonResult(
            @Language("graphql") String query, Map<String, Object> variables, ServerRequest serverRequest) {
        HttpHeaders httpHeaders = serverRequest != null ? serverRequest.headers().asHttpHeaders() : null;
        return execute(query, variables, null, httpHeaders, null, serverRequest).map(executionResult -> {
            if (!executionResult.getErrors().isEmpty()) {
                throw new QueryException(executionResult.getErrors());
            }

            return dgsJsonMapper.writeValueAsString(executionResult.toSpecification());
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<String, ? extends Object> map) {
        return (Map<String, Object>) (Map<?, ?>) map;
    }
}
