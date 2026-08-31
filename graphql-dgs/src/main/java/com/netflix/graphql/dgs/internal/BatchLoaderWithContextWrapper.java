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

import com.netflix.graphql.dgs.DgsDataLoaderRegistryConsumer;
import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.DataLoaderRegistry;

import java.util.List;
import java.util.concurrent.CompletionStage;

class BatchLoaderWithContextWrapper<K, V> implements BatchLoaderWithContext<K, V>, DgsDataLoaderRegistryConsumer {
    private final BatchLoader<K, V> original;

    BatchLoaderWithContextWrapper(BatchLoader<K, V> original) {
        this.original = original;
    }

    @Override
    public CompletionStage<List<V>> load(List<K> keys, BatchLoaderEnvironment environment) {
        return original.load(keys);
    }

    @Override
    public void setDataLoaderRegistry(DataLoaderRegistry dataLoaderRegistry) {
        if (original instanceof DgsDataLoaderRegistryConsumer consumer) {
            consumer.setDataLoaderRegistry(dataLoaderRegistry);
        }
    }
}
