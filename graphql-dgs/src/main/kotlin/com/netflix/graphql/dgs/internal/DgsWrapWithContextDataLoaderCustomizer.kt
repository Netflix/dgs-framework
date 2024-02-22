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

package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.DgsDataLoaderCustomizer
import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.BatchLoaderWithContext
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import java.util.concurrent.CompletionStage

class DgsWrapWithContextDataLoaderCustomizer : DgsDataLoaderCustomizer {
    override fun provide(original: BatchLoader<*, *>, name: String): Any {
        return BatchLoaderWithContextWrapper(original)
    }

    override fun provide(original: BatchLoaderWithContext<*, *>, name: String): Any {
        return original
    }

    override fun provide(original: MappedBatchLoader<*, *>, name: String): Any {
        return MappedBatchLoaderWithContextWrapper(original)
    }

    override fun provide(original: MappedBatchLoaderWithContext<*, *>, name: String): Any {
        return original
    }
}

internal class BatchLoaderWithContextWrapper<K, V>(private val original: BatchLoader<K, V>) : BatchLoaderWithContext<K, V> {
    override fun load(keys: List<K>, environment: BatchLoaderEnvironment): CompletionStage<List<V>> {
        return original.load(keys)
    }
}

internal class MappedBatchLoaderWithContextWrapper<K, V>(private val original: MappedBatchLoader<K, V>) : MappedBatchLoaderWithContext<K, V> {
    override fun load(keys: Set<K>, environment: BatchLoaderEnvironment): CompletionStage<Map<K, V>> {
        return original.load(keys)
    }
}
