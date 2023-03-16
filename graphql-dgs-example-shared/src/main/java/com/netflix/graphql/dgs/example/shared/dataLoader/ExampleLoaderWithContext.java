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

package com.netflix.graphql.dgs.example.shared.dataLoader;

import com.netflix.graphql.dgs.DgsDataLoader;
import com.netflix.graphql.dgs.context.DgsContext;
import com.netflix.graphql.dgs.example.shared.context.MyContext;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.BatchLoaderWithContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@DgsDataLoader(name = "exampleLoaderWithContext")
public class ExampleLoaderWithContext implements BatchLoaderWithContext<String, String> {
    @Override
    public CompletionStage<List<String>> load(List<String> keys, BatchLoaderEnvironment environment) {

        MyContext context = DgsContext.getCustomContext(environment);
        return CompletableFuture.supplyAsync(() -> keys.stream().map(key -> context.getCustomState() + " " + key).collect(Collectors.toList()));
    }
}
