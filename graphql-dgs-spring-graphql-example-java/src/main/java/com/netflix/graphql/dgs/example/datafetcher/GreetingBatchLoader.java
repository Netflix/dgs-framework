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

package com.netflix.graphql.dgs.example.datafetcher;

import graphql.schema.DataFetchingEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Controller
public class GreetingBatchLoader {
    public GreetingBatchLoader(BatchLoaderRegistry registry) {
        registry.forTypePair(Person.class, String.class).registerMappedBatchLoader((persons, env) -> {
            Map<Person, String> result = new HashMap<>();
            persons.forEach(it -> result.put(it, "greetings " + it.getName()));
            return Mono.just(result);
        });
    }

    @QueryMapping
    public CompletableFuture<String> greetingFromBatchLoader(@Argument Person person, DataLoader<Person, String> loader, DataFetchingEnvironment env) {
        DataLoaderRegistry registry = env.getDataLoaderRegistry();
        CompletableFuture<String> result = loader.load(person);
        loader.dispatch();
        return result;
    }
}
