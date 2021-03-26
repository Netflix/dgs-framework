/*
 * Copyright 2021 Netflix, Inc.
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

import com.netflix.graphql.dgs.DataLoaderInstrumentationExtensionProvider
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataLoader
import com.netflix.graphql.dgs.DgsDataLoaderOptionsCustomizer
import com.netflix.graphql.dgs.exceptions.InvalidDataLoaderTypeException
import com.netflix.graphql.dgs.exceptions.UnsupportedSecuredDataLoaderException
import com.netflix.graphql.dgs.internal.utils.DgsComponentUtils
import org.dataloader.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.ApplicationContext
import java.util.function.Supplier
import javax.annotation.PostConstruct

/**
 * Framework implementation class responsible for finding and configuring data loaders.
 */
class DgsDataLoaderProvider(private val applicationContext: ApplicationContext) {
    val logger: Logger = LoggerFactory.getLogger(DgsDataLoaderProvider::class.java)

    private val batchLoaders = mutableListOf<Pair<BatchLoader<*, *>, DgsDataLoader>>()
    private val batchLoadersWithContext = mutableListOf<Pair<BatchLoaderWithContext<*, *>, DgsDataLoader>>()
    private val mappedBatchLoaders = mutableListOf<Pair<MappedBatchLoader<*, *>, DgsDataLoader>>()
    private val mappedBatchLoadersWithContext = mutableListOf<Pair<MappedBatchLoaderWithContext<*, *>, DgsDataLoader>>()

    fun buildRegistry(): DataLoaderRegistry {
        return buildRegistryWithContextSupplier { null }
    }

    fun <T> buildRegistryWithContextSupplier(contextSupplier: Supplier<T>): DataLoaderRegistry {
        val startTime = System.currentTimeMillis()

        val dataLoaderRegistry = DataLoaderRegistry()
        batchLoaders.forEach { dataLoaderRegistry.register(it.second.name, createDataLoader(it.first, it.second)) }
        mappedBatchLoaders.forEach {
            dataLoaderRegistry.register(
                it.second.name,
                createDataLoader(it.first, it.second)
            )
        }
        batchLoadersWithContext.forEach {
            dataLoaderRegistry.register(
                it.second.name,
                createDataLoader(it.first, it.second, contextSupplier)
            )
        }
        mappedBatchLoadersWithContext.forEach {
            dataLoaderRegistry.register(
                it.second.name,
                createDataLoader(it.first, it.second, contextSupplier)
            )
        }

        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        logger.debug("Created DGS dataloader registry in {}ms", totalTime)

        return dataLoaderRegistry
    }

    @PostConstruct
    internal fun findDataLoaders() {
        addDataLoaderComponents()
        addDataLoaderFields()
    }

    private fun addDataLoaderFields() {
        applicationContext.getBeansWithAnnotation(DgsComponent::class.java).values.forEach { dgsComponent ->
            val javaClass = DgsComponentUtils.getClassEnhancedBySpringCGLib(dgsComponent)
            javaClass.declaredFields.filter { it.isAnnotationPresent(DgsDataLoader::class.java) }.forEach { field ->
                if (dgsComponent.javaClass.name.contains("EnhancerBySpringCGLIB")) {
                    throw UnsupportedSecuredDataLoaderException(dgsComponent::class.java)
                }

                val annotation = field.getAnnotation(DgsDataLoader::class.java)
                field.isAccessible = true
                when (val get = field.get(dgsComponent)) {
                    is BatchLoader<*, *> ->
                        batchLoaders.add(Pair(get, annotation))
                    is BatchLoaderWithContext<*, *> ->
                        batchLoadersWithContext.add(Pair(get, annotation))
                    is MappedBatchLoader<*, *> ->
                        mappedBatchLoaders.add(Pair(get, annotation))
                    is MappedBatchLoaderWithContext<*, *> ->
                        mappedBatchLoadersWithContext.add(Pair(get, annotation))
                    else -> throw InvalidDataLoaderTypeException(dgsComponent::class.java)
                }
            }
        }
    }

    private fun addDataLoaderComponents() {
        val dataLoaders = applicationContext.getBeansWithAnnotation(DgsDataLoader::class.java)
        dataLoaders.values.forEach { dgsComponent ->
            val javaClass = DgsComponentUtils.getClassEnhancedBySpringCGLib(dgsComponent)
            val annotation = javaClass.getAnnotation(DgsDataLoader::class.java)
            when (dgsComponent) {
                is BatchLoader<*, *> ->
                    batchLoaders.add(Pair(dgsComponent, annotation))
                is BatchLoaderWithContext<*, *> ->
                    batchLoadersWithContext.add(Pair(dgsComponent, annotation))
                is MappedBatchLoader<*, *> ->
                    mappedBatchLoaders.add(Pair(dgsComponent, annotation))
                is MappedBatchLoaderWithContext<*, *> ->
                    mappedBatchLoadersWithContext.add(Pair(dgsComponent, annotation))
                else -> throw InvalidDataLoaderTypeException(dgsComponent::class.java)
            }
        }
    }

    private fun createDataLoader(
        batchLoader: BatchLoader<*, *>,
        dgsDataLoader: DgsDataLoader
    ): DataLoader<out Any, out Any>? {
        val options = DataLoaderOptions.newOptions()
            .setBatchingEnabled(dgsDataLoader.batching)
            .setCachingEnabled(dgsDataLoader.caching)
        if (dgsDataLoader.maxBatchSize > 0) {
            options.setMaxBatchSize(dgsDataLoader.maxBatchSize)
        }
        customizeDataLoaderOptions(dgsDataLoader, options)
        val extendedBatchLoader = wrappedDataLoader(batchLoader, dgsDataLoader.name)
        return DataLoader.newDataLoader(extendedBatchLoader, options)
    }

    private fun createDataLoader(
        batchLoader: MappedBatchLoader<*, *>,
        dgsDataLoader: DgsDataLoader
    ): DataLoader<out Any, out Any>? {
        val options = DataLoaderOptions.newOptions()
            .setBatchingEnabled(dgsDataLoader.batching)
            .setCachingEnabled(dgsDataLoader.caching)
        if (dgsDataLoader.maxBatchSize > 0) {
            options.setMaxBatchSize(dgsDataLoader.maxBatchSize)
        }
        customizeDataLoaderOptions(dgsDataLoader, options)
        val extendedBatchLoader = wrappedDataLoader(batchLoader, dgsDataLoader.name)
        return DataLoader.newMappedDataLoader(extendedBatchLoader, options)
    }

    private fun <T> createDataLoader(
        batchLoader: BatchLoaderWithContext<*, *>,
        dgsDataLoader: DgsDataLoader,
        supplier: Supplier<T>
    ): DataLoader<out Any, out Any>? {
        val options = DataLoaderOptions.newOptions()
            .setBatchingEnabled(dgsDataLoader.batching)
            .setBatchLoaderContextProvider(supplier::get)
            .setCachingEnabled(dgsDataLoader.caching)

        if (dgsDataLoader.maxBatchSize > 0) {
            options.setMaxBatchSize(dgsDataLoader.maxBatchSize)
        }
        customizeDataLoaderOptions(dgsDataLoader, options)
        val extendedBatchLoader = wrappedDataLoader(batchLoader, dgsDataLoader.name)
        return DataLoader.newDataLoader(extendedBatchLoader, options)
    }

    private fun <T> createDataLoader(
        batchLoader: MappedBatchLoaderWithContext<*, *>,
        dgsDataLoader: DgsDataLoader,
        supplier: Supplier<T>
    ): DataLoader<out Any, out Any>? {
        val options = DataLoaderOptions.newOptions()
            .setBatchingEnabled(dgsDataLoader.batching)
            .setBatchLoaderContextProvider(supplier::get)
            .setCachingEnabled(dgsDataLoader.caching)

        if (dgsDataLoader.maxBatchSize > 0) {
            options.setMaxBatchSize(dgsDataLoader.maxBatchSize)
        }

        customizeDataLoaderOptions(dgsDataLoader, options)
        val extendedBatchLoader = wrappedDataLoader(batchLoader, dgsDataLoader.name)
        return DataLoader.newMappedDataLoader(extendedBatchLoader, options)
    }

    private fun customizeDataLoaderOptions(dgsDataLoader: DgsDataLoader, dataLoaderOptions: DataLoaderOptions){
        applicationContext
            .getBeanProvider(DgsDataLoaderOptionsCustomizer::class.java)
            .forEach{
                it.customize(dgsDataLoader, dataLoaderOptions)
            }
    }

    private inline fun <reified T> wrappedDataLoader(loader: T, name: String): T {
        try {
            val stream = applicationContext
                .getBeanProvider(DataLoaderInstrumentationExtensionProvider::class.java)
                .orderedStream()

            when (loader) {
                is BatchLoader<*, *> -> {
                    var wrappedBatchLoader: BatchLoader<*, *> = loader
                    stream.forEach { wrappedBatchLoader = it.provide(wrappedBatchLoader, name) }
                    return wrappedBatchLoader as T
                }
                is BatchLoaderWithContext<*, *> -> {
                    var wrappedBatchLoader: BatchLoaderWithContext<*, *> = loader
                    stream.forEach { wrappedBatchLoader = it.provide(wrappedBatchLoader, name) }
                    return wrappedBatchLoader as T
                }
                is MappedBatchLoader<*, *> -> {
                    var wrappedBatchLoader: MappedBatchLoader<*, *> = loader
                    stream.forEach { wrappedBatchLoader = it.provide(wrappedBatchLoader, name) }
                    return wrappedBatchLoader as T
                }
                is MappedBatchLoaderWithContext<*, *> -> {
                    var wrappedBatchLoader: MappedBatchLoaderWithContext<*, *> = loader
                    stream.forEach { wrappedBatchLoader = it.provide(wrappedBatchLoader, name) }
                    return wrappedBatchLoader as T
                }
            }
        } catch (ex: NoSuchBeanDefinitionException) {
            logger.debug("Unable to wrap the [{} : {}]", name, loader, ex)
        }
        return loader
    }
}
