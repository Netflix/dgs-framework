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
import java.util.function.Consumer
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
    private val dataLoaderRegistries: MutableMap<String, DataLoaderRegistry> = ConcurrentHashMap()

    /**
     * This will register a new dataloader
     *
     * @param key        the key to put the data loader under
     * @param dataLoader the data loader to register
     *
     * @return this registry
     */
    override fun register(key: String, dataLoader: DataLoader<*, *>): DataLoaderRegistry {
        val registry = ScheduledDataLoaderRegistry.newScheduledRegistry().register(key, dataLoader).build();
        dataLoaderRegistries.putIfAbsent(key, registry);
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
            .build();
        dataLoaderRegistries.putIfAbsent(key, registry);
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
        val dataLoadersForKey = dataLoaderRegistries[key]
        return if (dataLoadersForKey == null) {
            val newRegistry = ScheduledDataLoaderRegistry.newScheduledRegistry().build();
            dataLoaderRegistries[key] = newRegistry
            newRegistry.computeIfAbsent<K, V>(key, mappingFunction) as DataLoader<K, V>
        } else {
            dataLoadersForKey.computeIfAbsent<K, V>(key, mappingFunction!!) as DataLoader<K, V>;
        }
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
        return dataLoaderRegistries.flatMap { it.value.dataLoaders }
    }

    /**
     * @return the currently registered data loaders as a map
     */
    override fun getDataLoadersMap(): Map<String, DataLoader<*, *>> {
        var dataLoadersMap: Map<String, DataLoader<*, *>> = emptyMap()
        dataLoaderRegistries.forEach {
            dataLoadersMap = dataLoadersMap.plus(it.value.dataLoadersMap)
        }
        return LinkedHashMap(dataLoadersMap)
    }

    /**
     * This will unregister a new dataloader
     *
     * @param key the key of the data loader to unregister
     *
     * @return this registry
     */
    override fun unregister(key: String): DataLoaderRegistry {
        dataLoaderRegistries.remove(key)
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
    override fun <K, V> getDataLoader(key: String): DataLoader<K, V> {
        return dataLoaderRegistries[key]!!.dataLoadersMap[key] as DataLoader<K, V>
    }

    override fun getKeys(): Set<String> {
        return HashSet(dataLoaderRegistries.keys)
    }

    /**
     * This will be called [org.dataloader.DataLoader.dispatch] on each of the registered
     * [org.dataloader.DataLoader]s
     */
    override fun dispatchAll() {
        dataLoaderRegistries.forEach {
           it.value.dispatchAll()
        }
    }

    /**
     * Similar to [DataLoaderRegistry.dispatchAll], this calls [org.dataloader.DataLoader.dispatch] on
     * each of the registered [org.dataloader.DataLoader]s, but returns the number of dispatches.
     *
     * @return total number of entries that were dispatched from registered [org.dataloader.DataLoader]s.
     */
    override fun dispatchAllWithCount(): Int {
        var sum = 0
        dataLoaderRegistries.forEach {
            sum+= it.value.dispatchAllWithCount()
        }
        return sum
    }

    /**
     * @return The sum of all batched key loads that need to be dispatched from all registered
     * [org.dataloader.DataLoader]s
     */
    override fun dispatchDepth(): Int {
        var totalDispatchDepth = 0
        dataLoaderRegistries.forEach {
                totalDispatchDepth += it.value.dispatchDepth()
        }
        return totalDispatchDepth
    }

    override fun getStatistics() : Statistics {
        var stats = Statistics()
        dataLoaderRegistries.forEach {
                stats = stats.combine(it.value.statistics)
        }
        return stats
    }
}
