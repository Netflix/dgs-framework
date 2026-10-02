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

package com.netflix.graphql.dgs.metrics.micrometer.dataloader;

import com.netflix.graphql.dgs.DataLoaderInstrumentationExtensionProvider;
import com.netflix.graphql.dgs.metrics.micrometer.DgsMeterRegistrySupplier;
import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.MappedBatchLoader;
import org.dataloader.MappedBatchLoaderWithContext;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DgsDataLoaderInstrumentationProvider implements DataLoaderInstrumentationExtensionProvider {
    private final DgsMeterRegistrySupplier meterRegistrySupplier;

    private final Map<String, BatchLoader<?, ?>> batchLoaderClasses = new ConcurrentHashMap<>();
    private final Map<String, BatchLoaderWithContext<?, ?>> batchLoaderWithContextClasses = new ConcurrentHashMap<>();
    private final Map<String, MappedBatchLoader<?, ?>> mappedBatchLoaderClasses = new ConcurrentHashMap<>();
    private final Map<String, MappedBatchLoaderWithContext<?, ?>> mappedBatchLoaderWithContextClasses =
            new ConcurrentHashMap<>();

    public DgsDataLoaderInstrumentationProvider(@NotNull DgsMeterRegistrySupplier meterRegistrySupplier) {
        this.meterRegistrySupplier = meterRegistrySupplier;
    }

    @NotNull
    @Override
    public BatchLoader<?, ?> provide(@NotNull BatchLoader<?, ?> original, @NotNull String name) {
        return batchLoaderClasses.computeIfAbsent(
                name, key -> (BatchLoader<?, ?>) newProxy(BatchLoader.class, original, key));
    }

    @NotNull
    @Override
    public BatchLoaderWithContext<?, ?> provide(@NotNull BatchLoaderWithContext<?, ?> original, @NotNull String name) {
        return batchLoaderWithContextClasses.computeIfAbsent(
                name, key -> (BatchLoaderWithContext<?, ?>) newProxy(BatchLoaderWithContext.class, original, key));
    }

    @NotNull
    @Override
    public MappedBatchLoader<?, ?> provide(@NotNull MappedBatchLoader<?, ?> original, @NotNull String name) {
        return mappedBatchLoaderClasses.computeIfAbsent(
                name, key -> (MappedBatchLoader<?, ?>) newProxy(MappedBatchLoader.class, original, key));
    }

    @NotNull
    @Override
    public MappedBatchLoaderWithContext<?, ?> provide(@NotNull MappedBatchLoaderWithContext<?, ?> original, @NotNull String name) {
        return mappedBatchLoaderWithContextClasses.computeIfAbsent(
                name,
                key -> (MappedBatchLoaderWithContext<?, ?>)
                        newProxy(MappedBatchLoaderWithContext.class, original, key));
    }

    private Object newProxy(Class<?> loaderInterface, Object original, String name) {
        var handler = new BatchLoaderWithContextInterceptor(original, name, meterRegistrySupplier.get());
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {loaderInterface}, handler);
    }
}
