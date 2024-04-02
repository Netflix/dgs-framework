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

import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderWithContext
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext

/**
 * Beans that implement this interface will be called during the component scan
 * that finds defined @DgsDataLoaders and allows the modification or wrapping of
 * each DataLoader.
 *
 * While this hook appears very similar to [com.netflix.graphql.dgs.DataLoaderInstrumentationExtensionProvider]
 * there are two important differences:
 * - DgsDataLoaderCustomizers are called when scanning for @DgsDataLoaders, whereas the
 *   DataLoaderInstrumentationExtensionProviders are called once per request
 * - DgsDataLoaderCustomizers are afforded the opportunity to change the type of each DataLoader
 *   as it is being initially registered. Most notably, this allows for converting BatchLoader and MappedBatchLoader
 *   into their "WithContext" versions, which can simplify certain types of DataLoader instrumentation.
 */
interface DgsDataLoaderCustomizer {
    fun provide(original: BatchLoader<*, *>, name: String): Any {
        return original
    }
    fun provide(original: BatchLoaderWithContext<*, *>, name: String): Any {
        return original
    }
    fun provide(original: MappedBatchLoader<*, *>, name: String): Any {
        return original
    }
    fun provide(original: MappedBatchLoaderWithContext<*, *>, name: String): Any {
        return original
    }
}
