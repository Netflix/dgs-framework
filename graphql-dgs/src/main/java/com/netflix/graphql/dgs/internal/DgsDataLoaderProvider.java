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

import org.dataloader.DataLoaderRegistry;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * Interface for providing DataLoaderRegistry instances.
 *
 * <p>This interface abstracts the creation of DataLoaderRegistry instances,
 * allowing for different implementations including reloadable providers.
 *
 * <p>Implementations should handle the discovery and registration of all
 * @DgsDataLoader annotated components and provide them in a ready-to-use registry.
 */
public interface DgsDataLoaderProvider {
    /**
     * Builds a data loader registry with all discovered data loaders.
     *
     * @return DataLoaderRegistry instance containing all registered data loaders
     */
    DataLoaderRegistry buildRegistry();

    /**
     * Builds a data loader registry with a context supplier.
     * The context supplier provides access to the GraphQL context for data loaders.
     *
     * @param contextSupplier Supplier that provides GraphQL context
     * @return DataLoaderRegistry instance containing all registered data loaders
     */
    <T> DataLoaderRegistry buildRegistryWithContextSupplier(Supplier<T> contextSupplier);

    final class ScheduledExecutors {
        private ScheduledExecutors() {
        }

        /**
         * Creates a scheduled executor service for data loader operations.
         *
         * @return ScheduledExecutorService instance
         */
        public static ScheduledExecutorService createService(String qualifier) {
            return Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "dgs-dataloader-" + qualifier);
                thread.setDaemon(true);
                return thread;
            });
        }
    }
}
