/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.graphql.dgs

import org.dataloader.BatchLoaderEnvironment

/**
 * Provides a simple interface to define instrumentation around the dispatch of data loaders.
 *
 * Requires [com.netflix.graphql.dgs.internal.DgsWrapWithContextDataLoaderScanningInterceptor] to be enabled,
 * as DgsSimpleDataLoaderInstrumentation only provides hooks for the WithContext versions of batch loaders.
 */
interface DgsSimpleDataLoaderInstrumentation<C> {
    /**
     * beforeLoad will run before the data loader itself is actually called.
     *
     * @param name the name of the data loader
     * @param keys the list of keys dispatched to the data loader
     * @param batchLoaderEnvironment the batchLoaderEnvironment for the current execution
     *
     * @return context object that can be used to pass data to [afterLoad]
     */
    fun beforeLoad(name: String, keys: List<Any>, batchLoaderEnvironment: BatchLoaderEnvironment): C

    /**
     * afterLoad will run in a whenComplete attached to the data loader's returned CompletableFuture.
     *
     * @param name the name of the data loader
     * @param keys the list of keys dispatched to the data loader
     * @param batchLoaderEnvironment the batchLoaderEnvironment for the current execution
     * @param result the actual results of the data loader. Will be a Map or List depending on the type of data loader.
     * @param exception any exception thrown by the data loader (or other instrumentation)
     * @param instrumentationContext context object that allows [beforeLoad] to pass information (e.g. spanIds for tracing) to afterLoad
     *
     * This means it will run on one of two possible threads:
     *  - the thread associated with the CompletableFuture
     *  - the thread that actually dispatched the data loader
     */
    fun afterLoad(name: String, keys: List<Any>, batchLoaderEnvironment: BatchLoaderEnvironment, result: Any?, exception: Any?, instrumentationContext: C)

    /**
     * This allows for a type-safe implementation, even though the code that drives the instrumentation is unaware of
     * concrete context types involved.
     */
    @Suppress("UNCHECKED_CAST")
    fun afterLoadWithCast(name: String, keys: List<Any>, batchLoaderEnvironment: BatchLoaderEnvironment, result: Any?, exception: Any?, instrumentationContext: Any) {
        afterLoad(name, keys, batchLoaderEnvironment, result, exception, instrumentationContext as C)
    }
}
