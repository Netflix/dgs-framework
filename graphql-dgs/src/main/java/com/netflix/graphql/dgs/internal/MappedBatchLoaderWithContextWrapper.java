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
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.MappedBatchLoader;
import org.dataloader.MappedBatchLoaderWithContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

class MappedBatchLoaderWithContextWrapper<K, V>
        implements MappedBatchLoaderWithContext<K, V>, DgsDataLoaderRegistryConsumer {
    private final MappedBatchLoader<K, V> original;

    MappedBatchLoaderWithContextWrapper(MappedBatchLoader<K, V> original) {
        this.original = original;
    }

    @Override
    public CompletionStage<Map<K, V>> load(Set<K> keys, BatchLoaderEnvironment environment) {
        return original.load(keys);
    }

    @Override
    public void setDataLoaderRegistry(DataLoaderRegistry dataLoaderRegistry) {
        if (original instanceof DgsDataLoaderRegistryConsumer consumer) {
            consumer.setDataLoaderRegistry(dataLoaderRegistry);
        }
    }
}
