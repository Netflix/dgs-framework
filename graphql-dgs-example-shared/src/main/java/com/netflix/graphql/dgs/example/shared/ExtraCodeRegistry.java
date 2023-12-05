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

package com.netflix.graphql.dgs.example.shared;

import com.netflix.graphql.dgs.DgsCodeRegistry;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.example.shared.dataLoader.GreetingsDataLoader;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.registries.DispatchPredicate;
import org.dataloader.registries.ScheduledDataLoaderRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@DgsComponent
public class ExtraCodeRegistry {
    @DgsCodeRegistry
    public GraphQLCodeRegistry.Builder registry(GraphQLCodeRegistry.Builder codeRegistryBuilder, TypeDefinitionRegistry registry) {

        DataFetcher<String> df = (dfe) -> "yes, my extra field!";
        FieldCoordinates coordinates = FieldCoordinates.coordinates("Query", "myField");

       BatchLoader<String, String> batchLoader = keys -> CompletableFuture.supplyAsync(() -> {
            List<String> values = new ArrayList<>();
            values.add("a");
            values.add("b");
            values.add("c");
            return values;
        });
        DataLoader<String, String> dataloader = DataLoaderFactory.newDataLoader(batchLoader);
        DataFetcher<CompletableFuture<String>> dfWithDl = (dfe) -> {
            ScheduledDataLoaderRegistry scheduledRegistry = (ScheduledDataLoaderRegistry) dfe.getDataLoaderRegistry();
            scheduledRegistry.register("greetings", dataloader, DispatchPredicate.DISPATCH_ALWAYS);
            return dataloader.load("akjhsd");
        };
        FieldCoordinates testcoordinates = FieldCoordinates.coordinates("Query", "myGreetings");

        GraphQLCodeRegistry.Builder builder = codeRegistryBuilder.dataFetcher(coordinates, df).dataFetcher(testcoordinates, dfWithDl);
        return builder;
    }
}
