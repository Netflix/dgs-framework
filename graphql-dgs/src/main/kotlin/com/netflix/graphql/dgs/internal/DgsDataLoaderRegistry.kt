/*
 * Copyright 2023 Netflix, Inc.
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

import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import org.dataloader.registries.DispatchPredicate
import org.dataloader.registries.ScheduledDataLoaderRegistry
import org.dataloader.stats.Statistics
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/**
 * The DgsDataLoaderRegistry is a registry of DataLoaderRegistry instances. It supports specifying
 * DispatchPredicate on a per data loader basis, specified using @DispatchPredicate annotation. It creates an instance
 * of a ScheduledDataLoaderRegistry for every data loader that is registered and delegates to the mapping instance of
 * the registry based on the key. We need to create a registry per data loader since a DispatchPredicate is applicable
 * for an instance of the ScheduledDataLoaderRegistry.
 * https://github.com/graphql-java/java-dataloader#scheduled-dispatching
 */
open class DgsDataLoaderRegistry : DataLoaderRegistry() {
    private val scheduledDataLoaderRegistries: MutableMap<String, DataLoaderRegistry> = ConcurrentHashMap()
    private val dataLoaderRegistry = DataLoaderRegistry()

    /**
     * This will register a new dataloader
     *
     * @param key        the key to put the data loader under
     * @param dataLoader the data loader to register
     *
     * @return this registry
     */
    override fun register(key: String, dataLoader: DataLoader<*, *>): DataLoaderRegistry {
        dataLoaderRegistry.register(key, dataLoader)
        return this
    }

    /**
     * This will register a new dataloader with a dispatch predicate set up for that loader
     *
     * @param key        the key to put the data loader under
     * @param dataLoader the data loader to register
     *
     * @return this registry
     */
    fun registerWithDispatchPredicate(
        key: String,
        dataLoader: DataLoader<*, *>,
        dispatchPredicate: DispatchPredicate
    ): DataLoaderRegistry {
        val registry = ScheduledDataLoaderRegistry.newScheduledRegistry().register(key, dataLoader)
            .dispatchPredicate(dispatchPredicate)
            .build()
        scheduledDataLoaderRegistries.putIfAbsent(key, registry)
        return this
    }

    /**
     * Computes a data loader if absent or return it if it was
     * already registered at that key.
     *
     *
     * Note: The entire method invocation is performed atomically,
     * so the function is applied at most once per key.
     *
     * @param key             the key of the data loader
     * @param mappingFunction the function to compute a data loader
     * @param <K>             the type of keys
     * @param <V>             the type of values
     *
     * @return a data loader
     </V></K> */
    override fun <K, V> computeIfAbsent(
        key: String,
        mappingFunction: Function<String, DataLoader<*, *>>?
    ): DataLoader<K, V> {
        // we do not support this method for registering with dispatch predicates
        return dataLoaderRegistry.computeIfAbsent<K, V>(key, mappingFunction!!) as DataLoader<K, V>
    }

    /**
     *  This operation is not supported since we cannot store a dataloader registry without a key.
     */
    override fun combine(registry: DataLoaderRegistry): DataLoaderRegistry? {
        throw UnsupportedOperationException("Cannot combine a DgsDataLoaderRegistry with another registry")
    }

    /**
     * @return the currently registered data loaders
     */
    override fun getDataLoaders(): List<DataLoader<*, *>> {
        return scheduledDataLoaderRegistries.flatMap { it.value.dataLoaders }.plus(dataLoaderRegistry.dataLoaders)
    }

    /**
     * @return the currently registered data loaders as a map
     */
    override fun getDataLoadersMap(): Map<String, DataLoader<*, *>> {
        var dataLoadersMap: Map<String, DataLoader<*, *>> = emptyMap()
        scheduledDataLoaderRegistries.forEach {
            dataLoadersMap = dataLoadersMap.plus(it.value.dataLoadersMap)
        }
        return LinkedHashMap(dataLoadersMap.plus(dataLoaderRegistry.dataLoadersMap))
    }

    /**
     * This will unregister a new dataloader
     *
     * @param key the key of the data loader to unregister
     *
     * @return this registry
     */
    override fun unregister(key: String): DataLoaderRegistry {
        scheduledDataLoaderRegistries.remove(key)
        dataLoaderRegistry.unregister(key)
        return this
    }

    /**
     * Returns the dataloader that was registered under the specified key
     *
     * @param key the key of the data loader
     * @param <K> the type of keys
     * @param <V> the type of values
     *
     * @return a data loader or null if its not present
     </V></K> */
    override fun <K, V> getDataLoader(key: String): DataLoader<K, V>? {
        return dataLoaderRegistry.getDataLoader(key) ?: scheduledDataLoaderRegistries[key]?.getDataLoader(key)
    }

    override fun getKeys(): Set<String> {
        return scheduledDataLoaderRegistries.keys.plus(dataLoaderRegistry.keys)
    }

    /**
     * This will be called [org.dataloader.DataLoader.dispatch] on each of the registered
     * [org.dataloader.DataLoader]s
     */
    override fun dispatchAll() {
        scheduledDataLoaderRegistries.forEach {
            it.value.dispatchAll()
        }
        dataLoaderRegistry.dispatchAll()
    }

    /**
     * Similar to [DataLoaderRegistry.dispatchAll], this calls [org.dataloader.DataLoader.dispatch] on
     * each of the registered [org.dataloader.DataLoader]s, but returns the number of dispatches.
     *
     * @return total number of entries that were dispatched from registered [org.dataloader.DataLoader]s.
     */
    override fun dispatchAllWithCount(): Int {
        var sum = 0
        scheduledDataLoaderRegistries.forEach {
            sum += it.value.dispatchAllWithCount()
        }
        sum += dataLoaderRegistry.dispatchAllWithCount()
        return sum
    }

    /**
     * @return The sum of all batched key loads that need to be dispatched from all registered
     * [org.dataloader.DataLoader]s
     */
    override fun dispatchDepth(): Int {
        var totalDispatchDepth = 0
        scheduledDataLoaderRegistries.forEach {
            totalDispatchDepth += it.value.dispatchDepth()
        }
        totalDispatchDepth += dataLoaderRegistry.dispatchDepth()

        return totalDispatchDepth
    }

    override fun getStatistics(): Statistics {
        var stats = Statistics()
        scheduledDataLoaderRegistries.forEach {
            stats = stats.combine(it.value.statistics)
        }
        stats = stats.combine(dataLoaderRegistry.statistics)
        return stats
    }
}
