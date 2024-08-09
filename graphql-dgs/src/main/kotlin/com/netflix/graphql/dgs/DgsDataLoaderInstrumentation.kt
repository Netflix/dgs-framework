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
 * Requires [com.netflix.graphql.dgs.internal.DgsWrapWithContextDataLoaderCustomizer] to be enabled,
 * as DgsDataLoaderInstrumentation only provides hooks for the WithContext versions of batch loaders.
 */
interface DgsDataLoaderInstrumentation {
    /**
     * onDispatch will run before the data loader itself is actually called.
     *
     * @param name the name of the data loader
     * @param keys the list of keys dispatched to the data loader
     * @param batchLoaderEnvironment the batchLoaderEnvironment for the current execution
     *
     * @return context object that also contains the other hooks
     */
    fun onDispatch(
        name: String,
        keys: List<Any>,
        batchLoaderEnvironment: BatchLoaderEnvironment,
    ): DgsDataLoaderInstrumentationContext
}
