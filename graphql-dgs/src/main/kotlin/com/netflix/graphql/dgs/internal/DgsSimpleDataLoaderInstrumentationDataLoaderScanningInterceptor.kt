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

import com.netflix.graphql.dgs.DgsDataLoaderScanningInterceptor
import com.netflix.graphql.dgs.DgsSimpleDataLoaderInstrumentation
import com.netflix.graphql.dgs.exceptions.DgsSimpleDataLoaderInstrumentationException
import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.BatchLoaderWithContext
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import java.util.concurrent.CompletionStage

class DgsSimpleDataLoaderInstrumentationDataLoaderScanningInterceptor(
    private val instrumentations: List<DgsSimpleDataLoaderInstrumentation<*>>
) : DgsDataLoaderScanningInterceptor {
    override fun provide(original: BatchLoader<*, *>, name: String): Any {
        throw DgsSimpleDataLoaderInstrumentationException(name)
    }

    override fun provide(original: BatchLoaderWithContext<*, *>, name: String): Any {
        return BatchLoaderWithContextSimpleInstrumentationDriver(original, name, instrumentations)
    }

    override fun provide(original: MappedBatchLoader<*, *>, name: String): Any {
        throw DgsSimpleDataLoaderInstrumentationException(name)
    }

    override fun provide(original: MappedBatchLoaderWithContext<*, *>, name: String): Any {
        return MappedBatchLoaderWithContextSimpleInstrumentationDriver(original, name, instrumentations)
    }
}

internal class BatchLoaderWithContextSimpleInstrumentationDriver<K : Any, V>(
    private val original: BatchLoaderWithContext<K, V>,
    private val name: String,
    private val instrumentations: List<DgsSimpleDataLoaderInstrumentation<*>>
) : BatchLoaderWithContext<K, V> {
    override fun load(keys: MutableList<K>, environment: BatchLoaderEnvironment): CompletionStage<MutableList<V>> {
        val contexts = instrumentations.map { it.beforeLoad(name, keys, environment) }

        val future = original.load(keys, environment)

        return instrumentations.zip(contexts).reversed().fold(future) { f, p ->
            f.whenComplete { result, exception ->
                p.first.afterLoadWithCast(name, keys, environment, result, exception, p.second!!)
            }
        }
    }
}

internal class MappedBatchLoaderWithContextSimpleInstrumentationDriver<K : Any, V>(
    private val original: MappedBatchLoaderWithContext<K, V>,
    private val name: String,
    private val instrumentations: List<DgsSimpleDataLoaderInstrumentation<*>>
) : MappedBatchLoaderWithContext<K, V> {
    override fun load(keys: MutableSet<K>, environment: BatchLoaderEnvironment): CompletionStage<MutableMap<K, V>> {
        val keysList = ArrayList(keys)
        val contexts = instrumentations.map { it.beforeLoad(name, keysList, environment) }

        val future = original.load(keys, environment)

        return instrumentations.zip(contexts).reversed().fold(future) { f, p ->
            f.whenComplete { result, exception ->
                p.first.afterLoadWithCast(name, keysList, environment, result, exception, p.second!!)
            }
        }
    }
}
