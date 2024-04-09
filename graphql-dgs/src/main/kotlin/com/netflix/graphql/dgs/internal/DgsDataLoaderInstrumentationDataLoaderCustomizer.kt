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
import com.netflix.graphql.dgs.DgsDataLoaderInstrumentation
import com.netflix.graphql.dgs.DgsDataLoaderRegistryConsumer
import com.netflix.graphql.dgs.exceptions.DgsDataLoaderInstrumentationException
import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.BatchLoaderWithContext
import org.dataloader.DataLoaderRegistry
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import java.util.concurrent.CompletionStage

class DgsDataLoaderInstrumentationDataLoaderCustomizer(
    private val instrumentations: List<DgsDataLoaderInstrumentation>
) : DgsDataLoaderCustomizer {
    override fun provide(original: BatchLoader<*, *>, name: String): Any {
        throw DgsDataLoaderInstrumentationException(name)
    }

    override fun provide(original: BatchLoaderWithContext<*, *>, name: String): Any {
        return BatchLoaderWithContextInstrumentationDriver(original, name, instrumentations)
    }

    override fun provide(original: MappedBatchLoader<*, *>, name: String): Any {
        throw DgsDataLoaderInstrumentationException(name)
    }

    override fun provide(original: MappedBatchLoaderWithContext<*, *>, name: String): Any {
        return MappedBatchLoaderWithContextInstrumentationDriver(original, name, instrumentations)
    }
}

internal class BatchLoaderWithContextInstrumentationDriver<K : Any, V>(
    private val original: BatchLoaderWithContext<K, V>,
    private val name: String,
    private val instrumentations: List<DgsDataLoaderInstrumentation>
) : BatchLoaderWithContext<K, V>, DgsDataLoaderRegistryConsumer {
    override fun load(keys: List<K>, environment: BatchLoaderEnvironment): CompletionStage<List<V>> {
        val contexts = instrumentations.map { it.onDispatch(name, keys, environment) }
        val future = original.load(keys, environment)

        return future.whenComplete { result, exception ->
            try {
                contexts.asReversed().forEach { c ->
                    c.onComplete(result, exception)
                }
            } catch (_: Throwable) {
            }
        }
    }

    override fun setDataLoaderRegistry(dataLoaderRegistry: DataLoaderRegistry?) {
        if (original is DgsDataLoaderRegistryConsumer) {
            (original as DgsDataLoaderRegistryConsumer).setDataLoaderRegistry(dataLoaderRegistry)
        }
    }
}

internal class MappedBatchLoaderWithContextInstrumentationDriver<K : Any, V>(
    private val original: MappedBatchLoaderWithContext<K, V>,
    private val name: String,
    private val instrumentations: List<DgsDataLoaderInstrumentation>
) : MappedBatchLoaderWithContext<K, V>, DgsDataLoaderRegistryConsumer {
    override fun load(keys: Set<K>, environment: BatchLoaderEnvironment): CompletionStage<Map<K, V>> {
        val keysList = keys.toList()
        val contexts = instrumentations.map { it.onDispatch(name, keysList, environment) }

        val future = original.load(keys, environment)

        return future.whenComplete { result, exception ->
            try {
                contexts.asReversed().forEach { c ->
                    c.onComplete(result, exception)
                }
            } catch (_: Throwable) {
            }
        }
    }

    override fun setDataLoaderRegistry(dataLoaderRegistry: DataLoaderRegistry?) {
        if (original is DgsDataLoaderRegistryConsumer) {
            (original as DgsDataLoaderRegistryConsumer).setDataLoaderRegistry(dataLoaderRegistry)
        }
    }
}
