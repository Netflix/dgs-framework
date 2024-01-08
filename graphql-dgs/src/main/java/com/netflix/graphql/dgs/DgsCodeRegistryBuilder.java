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

package com.netflix.graphql.dgs;

import com.netflix.graphql.dgs.internal.DataFetcherResultProcessor;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactories;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;

import java.util.List;

/**
 * Utility wrapper for {@link GraphQLCodeRegistry.Builder} which provides
 * a consistent registration mechanism of DataFetchers similar to the annotation-based approach.
 * Can be used as a first parameter of a {@link DgsCodeRegistry} annotated method.
 */
public class DgsCodeRegistryBuilder {

    private final List<DataFetcherResultProcessor> dataFetcherResultProcessors;

    private final GraphQLCodeRegistry.Builder graphQLCodeRegistry;

    public DgsCodeRegistryBuilder(
            List<DataFetcherResultProcessor> dataFetcherResultProcessors,
            GraphQLCodeRegistry.Builder codeRegistry) {
        this.dataFetcherResultProcessors = dataFetcherResultProcessors;
        this.graphQLCodeRegistry = codeRegistry;
    }

    public DgsCodeRegistryBuilder dataFetcher(FieldCoordinates coordinates, DataFetcher<?> dataFetcher) {
        var wrapped = DataFetcherFactories.wrapDataFetcher(dataFetcher, (dfe, result) -> {
            if (result == null || coordinates.getTypeName().equals("Subscription")) {
                return result;
            }

            var dgsDfe = new DgsDataFetchingEnvironment(dfe);
            return dataFetcherResultProcessors.stream()
                    .filter(p -> p.supportsType(result))
                    .findFirst()
                    .map(p -> p.process(result, dgsDfe))
                    .orElse(result);
        });

        graphQLCodeRegistry.dataFetcher(coordinates, wrapped);
        return this;
    }

    public boolean hasDataFetcher(FieldCoordinates coordinates) {
        return graphQLCodeRegistry.hasDataFetcher(coordinates);
    }

    public DataFetcher<?> getDataFetcher(FieldCoordinates coordinates, GraphQLFieldDefinition fieldDefinition) {
        return graphQLCodeRegistry.getDataFetcher(coordinates, fieldDefinition);
    }

    public GraphQLCodeRegistry.Builder getGraphQLCodeRegistry() {
        return graphQLCodeRegistry;
    }
}
