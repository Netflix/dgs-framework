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

import com.netflix.graphql.dgs.DgsDataLoaderCustomizer;
import com.netflix.graphql.dgs.DgsDataLoaderInstrumentation;
import com.netflix.graphql.dgs.DgsDataLoaderInstrumentationContext;
import com.netflix.graphql.dgs.DgsDataLoaderRegistryConsumer;
import com.netflix.graphql.dgs.exceptions.DgsDataLoaderInstrumentationException;
import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.MappedBatchLoader;
import org.dataloader.MappedBatchLoaderWithContext;
import org.dataloader.instrumentation.DataLoaderInstrumentationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class DgsDataLoaderInstrumentationDataLoaderCustomizer implements DgsDataLoaderCustomizer {
    private final List<DgsDataLoaderInstrumentation> instrumentations;

    public DgsDataLoaderInstrumentationDataLoaderCustomizer(List<DgsDataLoaderInstrumentation> instrumentations) {
        this.instrumentations = instrumentations;
    }

    @Override
    public Object provide(BatchLoader<?, ?> original, String name) {
        throw new DgsDataLoaderInstrumentationException(name);
    }

    @Override
    public Object provide(BatchLoaderWithContext<?, ?> original, String name) {
        return new BatchLoaderWithContextInstrumentationDriver<>(original, name, instrumentations);
    }

    @Override
    public Object provide(MappedBatchLoader<?, ?> original, String name) {
        throw new DgsDataLoaderInstrumentationException(name);
    }

    @Override
    public Object provide(MappedBatchLoaderWithContext<?, ?> original, String name) {
        return new MappedBatchLoaderWithContextInstrumentationDriver<>(original, name, instrumentations);
    }

    static class BatchLoaderWithContextInstrumentationDriver<K, V>
            implements BatchLoaderWithContext<K, V>, DgsDataLoaderRegistryConsumer {
        private final BatchLoaderWithContext<K, V> original;
        private final String name;
        private final List<DgsDataLoaderInstrumentation> instrumentations;

        BatchLoaderWithContextInstrumentationDriver(
                BatchLoaderWithContext<K, V> original,
                String name,
                List<DgsDataLoaderInstrumentation> instrumentations) {
            this.original = original;
            this.name = name;
            this.instrumentations = instrumentations;
        }

        @Override
        public CompletionStage<List<V>> load(List<K> keys, BatchLoaderEnvironment environment) {
            List<DgsDataLoaderInstrumentationContext> contexts =
                instrumentations.stream()
                    .map(it -> it.onDispatch(name, keys, environment))
                    .toList();

            CompletionStage<List<V>> future = original.load(keys, environment).whenComplete((result, exception) -> {
                try {
                    List<DgsDataLoaderInstrumentationContext> reversedContexts =
                        new ArrayList<>(contexts);
                    Collections.reverse(reversedContexts);

                    for (DgsDataLoaderInstrumentationContext c : reversedContexts) {
                        c.onComplete(result, exception);
                    }
                } catch (Throwable ignored) {
                    System.out.println("Error");
                }
            });

            return future;
        }

        @Override
        public void setDataLoaderRegistry(DataLoaderRegistry dataLoaderRegistry) {
            if (original instanceof DgsDataLoaderRegistryConsumer) {
                ((DgsDataLoaderRegistryConsumer) original).setDataLoaderRegistry(dataLoaderRegistry);
            }
        }
    }

    static class MappedBatchLoaderWithContextInstrumentationDriver<K, V>
            implements MappedBatchLoaderWithContext<K, V>, DgsDataLoaderRegistryConsumer {
        private final MappedBatchLoaderWithContext<K, V> original;
        private final String name;
        private final List<DgsDataLoaderInstrumentation> instrumentations;

        MappedBatchLoaderWithContextInstrumentationDriver(
                MappedBatchLoaderWithContext<K, V> original,
                String name,
                List<DgsDataLoaderInstrumentation> instrumentations) {
            this.original = original;
            this.name = name;
            this.instrumentations = instrumentations;
        }

        @Override
        public CompletionStage<Map<K, V>> load(Set<K> keys, BatchLoaderEnvironment environment) {
            List<DgsDataLoaderInstrumentationContext> contexts =
                instrumentations.stream()
                    .map(it -> it.onDispatch(name, new ArrayList<>(keys), environment))
                    .toList();

            CompletionStage<Map<K, V>> future = original.load(keys, environment);

            return future.whenComplete((result, exception) -> {
                try {
                    List<DgsDataLoaderInstrumentationContext> reversedContexts =
                        new ArrayList<>(contexts);
                    Collections.reverse(reversedContexts);

                    for (DgsDataLoaderInstrumentationContext c : reversedContexts) {
                        c.onComplete(result, exception);
                    }
                } catch (Throwable ignored) {
                    // Silently catch and ignore
                }
            });
        }

        @Override
        public void setDataLoaderRegistry(DataLoaderRegistry dataLoaderRegistry) {
            if (original instanceof DgsDataLoaderRegistryConsumer) {
                ((DgsDataLoaderRegistryConsumer) original).setDataLoaderRegistry(dataLoaderRegistry);
            }
        }
    }
}