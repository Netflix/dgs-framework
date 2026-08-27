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

package com.netflix.graphql.dgs;

import com.netflix.graphql.dgs.internal.DataFetcherResultProcessor;
import graphql.TrivialDataFetcher;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactories;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * Utility wrapper for {@link GraphQLCodeRegistry.Builder} which provides
 * a consistent registration mechanism of DataFetchers similar to the annotation-based approach.
 * Can be used as a first parameter of a {@link DgsCodeRegistry} annotated method.
 */
public class DgsCodeRegistryBuilder {
    private final List<DataFetcherResultProcessor> dataFetcherResultProcessors;
    private final GraphQLCodeRegistry.Builder graphQLCodeRegistry;
    private final ApplicationContext ctx;

    public DgsCodeRegistryBuilder(
            List<DataFetcherResultProcessor> dataFetcherResultProcessors,
            GraphQLCodeRegistry.Builder graphQLCodeRegistry,
            ApplicationContext ctx) {
        this.dataFetcherResultProcessors = dataFetcherResultProcessors;
        this.graphQLCodeRegistry = graphQLCodeRegistry;
        this.ctx = ctx;
    }

    public DgsCodeRegistryBuilder dataFetcher(FieldCoordinates coordinates, DataFetcher<?> dataFetcher) {
        DataFetcher<?> fetcher =
                !dataFetcherResultProcessors.isEmpty() && !(dataFetcher instanceof TrivialDataFetcher)
                        ? DataFetcherFactories.wrapDataFetcher(dataFetcher, this::convertResult)
                        : dataFetcher;

        graphQLCodeRegistry.dataFetcher(coordinates, fetcher);
        return this;
    }

    public boolean hasDataFetcher(FieldCoordinates coordinates) {
        return graphQLCodeRegistry.hasDataFetcher(coordinates);
    }

    public DataFetcher<?> getDataFetcher(FieldCoordinates coordinates, GraphQLFieldDefinition fieldDefinition) {
        return graphQLCodeRegistry.getDataFetcher(coordinates, fieldDefinition);
    }

    private Object convertResult(DataFetchingEnvironment dfe, Object result) {
        if (result == null) {
            return null;
        }
        DataFetcherResultProcessor processor = dataFetcherResultProcessors.stream()
                .filter(candidate -> candidate.supportsType(result))
                .findFirst()
                .orElse(null);
        if (processor == null) {
            return result;
        }
        DgsDataFetchingEnvironment env = dfe instanceof DgsDataFetchingEnvironment dgsEnv
                ? dgsEnv
                : new DgsDataFetchingEnvironment(dfe, ctx);
        return processor.process(result, env);
    }
}
