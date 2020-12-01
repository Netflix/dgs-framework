/*
 * Copyright 2020 Netflix, Inc.
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

import org.dataloader.MappedBatchLoader;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class ExampleMappedBatchLoaderFromField {
    @DgsDataLoader(name = "exampleMappedLoaderFromField")
    public MappedBatchLoader<String, String> mappedLoader = keys -> CompletableFuture.supplyAsync(() -> {
        HashMap<String, String> result = new HashMap<>();
        result.put("a", "A");
        result.put("b", "B");
        result.put("c", "C");

        return result;
    });

    @DgsDataLoader(name = "privateExampleMappedLoaderFromField")
    MappedBatchLoader<String, String> privateMappedLoader = keys -> CompletableFuture.supplyAsync(() -> {
        HashMap<String, String> result = new HashMap<>();
        result.put("a", "A");
        result.put("b", "B");
        result.put("c", "C");

        return result;
    });
}
