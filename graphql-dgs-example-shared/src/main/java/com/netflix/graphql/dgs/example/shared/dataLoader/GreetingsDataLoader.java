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

package com.netflix.graphql.dgs.example.shared.dataLoader;

import com.netflix.graphql.dgs.DgsDataLoader;
import com.netflix.graphql.dgs.DgsDispatchPredicate;
import org.dataloader.BatchLoader;
import org.dataloader.registries.DispatchPredicate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@DgsDataLoader(name = "greetings")
public class GreetingsDataLoader implements BatchLoader<String, String> {
    @Override
    public CompletionStage<List<String>> load(List<String> keys) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return CompletableFuture.supplyAsync(() -> keys.stream().map(key -> "Greetings, " + key + "!").collect(Collectors.toList()));
    }
}

