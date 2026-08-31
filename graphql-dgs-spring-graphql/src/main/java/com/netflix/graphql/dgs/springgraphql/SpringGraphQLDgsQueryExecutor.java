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
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.context.DgsContext;
import com.netflix.graphql.dgs.context.GraphQLContextContributor;
import com.netflix.graphql.dgs.exceptions.DgsQueryExecutionDataExtractionException;
import com.netflix.graphql.dgs.exceptions.QueryException;
import com.netflix.graphql.dgs.internal.DefaultDgsGraphQLContextBuilder;
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider;
import com.netflix.graphql.dgs.internal.DgsQueryExecutorRequestCustomizer;
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData;
import com.netflix.graphql.dgs.json.DgsJsonMapper;
import graphql.ExecutionResult;
import org.dataloader.DataLoaderRegistry;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.support.DefaultExecutionGraphQlRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class SpringGraphQLDgsQueryExecutor implements DgsQueryExecutor {
    private final ExecutionGraphQlService executionService;
    private final DefaultDgsGraphQLContextBuilder dgsContextBuilder;
    private final DgsDataLoaderProvider dgsDataLoaderProvider;
    private final DgsJsonMapper dgsJsonMapper;
    private final DgsQueryExecutorRequestCustomizer requestCustomizer;
    private final List<GraphQLContextContributor> graphQLContextContributors;
    private final ParseContext parseContext;

    public SpringGraphQLDgsQueryExecutor(
            ExecutionGraphQlService executionService,
            DefaultDgsGraphQLContextBuilder dgsContextBuilder,
            DgsDataLoaderProvider dgsDataLoaderProvider,
            DgsJsonMapper dgsJsonMapper,
            DgsQueryExecutorRequestCustomizer requestCustomizer,
            List<GraphQLContextContributor> graphQLContextContributors) {
        this.executionService = executionService;
        this.dgsContextBuilder = dgsContextBuilder;
        this.dgsDataLoaderProvider = dgsDataLoaderProvider;
        this.dgsJsonMapper = dgsJsonMapper;
        this.requestCustomizer = requestCustomizer;
        this.graphQLContextContributors = graphQLContextContributors;
        this.parseContext = JsonPath.using(dgsJsonMapper.jsonPathConfiguration());
    }

    public SpringGraphQLDgsQueryExecutor(
            ExecutionGraphQlService executionService,
            DefaultDgsGraphQLContextBuilder dgsContextBuilder,
            DgsDataLoaderProvider dgsDataLoaderProvider,
            DgsJsonMapper dgsJsonMapper,
            List<GraphQLContextContributor> graphQLContextContributors) {
        this(
                executionService,
                dgsContextBuilder,
                dgsDataLoaderProvider,
                dgsJsonMapper,
                DgsQueryExecutorRequestCustomizer.DEFAULT_REQUEST_CUSTOMIZER,
                graphQLContextContributors);
    }

    @Override
    public ExecutionResult execute(
            String query,
            Map<String, Object> variables,
            Map<String, Object> extensions,
            HttpHeaders headers,
            String operationName,
            WebRequest webRequest) {
        DefaultExecutionGraphQlRequest request =
                new DefaultExecutionGraphQlRequest(query, operationName, variables, extensions, "", null);

        WebRequest currentRequest = webRequest;
        if (currentRequest == null) {
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            currentRequest = requestAttributes instanceof WebRequest attributes ? attributes : null;
        }
        WebRequest httpRequest = requestCustomizer.apply(currentRequest, headers);
        DgsContext dgsContext =
                dgsContextBuilder.build(new DgsWebMvcRequestData(request.getExtensions(), headers, httpRequest));

        // A ticker mode registry keeps rescheduling itself until it is closed, so the registry built
        // for this request has to be closed once the request completes, the same way the webmvc and
        // webflux interceptors do it. Otherwise every query leaves a task behind on the shared
        // scheduled executor for the rest of the JVM's life.
        AtomicReference<DataLoaderRegistry> dataLoaderRegistry = new AtomicReference<>();

        request.configureExecutionInput((executionInput, builder) -> {
            DataLoaderRegistry registry =
                    dgsDataLoaderProvider.buildRegistryWithContextSupplier(executionInput::getGraphQLContext);
            dataLoaderRegistry.set(registry);
            return builder.graphQLContext(dgsContext)
                    .dataLoaderRegistry(registry)
                    .build();
        });

        ExecutionGraphQlResponse response = executionService
                .execute(request)
                .doFinally(signalType -> {
                    if (dataLoaderRegistry.get() instanceof AutoCloseable closeable) {
                        try {
                            closeable.close();
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    }
                })
                .block();
        if (response == null) {
            throw new IllegalStateException("Unexpected null response from Spring GraphQL client");
        }

        return response.getExecutionResult();
    }

    @Override
    public <T> T executeAndExtractJsonPath(String query, String jsonPath, Map<String, Object> variables) {
        return JsonPath.read(getJsonResult(query, variables, null, null), jsonPath);
    }

    @Override
    public <T> T executeAndExtractJsonPath(String query, String jsonPath, HttpHeaders headers) {
        return JsonPath.read(getJsonResult(query, Map.of(), headers, null), jsonPath);
    }

    @Override
    public <T> T executeAndExtractJsonPath(String query, String jsonPath, ServletWebRequest servletWebRequest) {
        HttpHeaders httpHeaders = new HttpHeaders();
        Iterator<String> headerNames = servletWebRequest.getHeaderNames();
        while (headerNames.hasNext()) {
            String name = headerNames.next();
            String[] values = servletWebRequest.getHeaderValues(name);
            httpHeaders.addAll(name, values != null ? List.of(values) : List.of());
        }

        return JsonPath.read(getJsonResult(query, Map.of(), httpHeaders, servletWebRequest), jsonPath);
    }

    @Override
    public DocumentContext executeAndGetDocumentContext(String query, Map<String, Object> variables) {
        return parseContext.parse(getJsonResult(query, variables, null, null));
    }

    @Override
    public DocumentContext executeAndGetDocumentContext(
            String query, Map<String, Object> variables, HttpHeaders headers) {
        return parseContext.parse(getJsonResult(query, variables, headers, null));
    }

    @Override
    public <T> T executeAndExtractJsonPathAsObject(
            String query, String jsonPath, Map<String, Object> variables, Class<T> clazz, HttpHeaders headers) {
        String jsonResult = getJsonResult(query, variables, headers, null);
        try {
            return parseContext.parse(jsonResult).read(jsonPath, clazz);
        } catch (MappingException ex) {
            throw new DgsQueryExecutionDataExtractionException(ex, jsonResult, jsonPath, clazz);
        }
    }

    @Override
    public <T> T executeAndExtractJsonPathAsObject(
            String query, String jsonPath, Map<String, Object> variables, TypeRef<T> typeRef, HttpHeaders headers) {
        String jsonResult = getJsonResult(query, variables, headers, null);
        try {
            return parseContext.parse(jsonResult).read(jsonPath, typeRef);
        } catch (MappingException ex) {
            throw new DgsQueryExecutionDataExtractionException(ex, jsonResult, jsonPath, typeRef);
        }
    }

    private String getJsonResult(
            String query, Map<String, Object> variables, HttpHeaders headers, ServletWebRequest servletWebRequest) {
        ExecutionResult executionResult = execute(query, variables, null, headers, null, servletWebRequest);

        if (!executionResult.getErrors().isEmpty()) {
            throw new QueryException(executionResult.getErrors());
        }

        return dgsJsonMapper.writeValueAsString(executionResult.toSpecification());
    }
}
