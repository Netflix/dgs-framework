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
import com.netflix.graphql.dgs.DgsDataLoaderRegistryConsumer
import com.netflix.graphql.dgs.exceptions.InvalidDataLoaderTypeException
import com.netflix.graphql.dgs.exceptions.UnsupportedSecuredDataLoaderException
import org.dataloader.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.ApplicationContext
import org.springframework.util.ReflectionUtils
import java.util.function.Supplier
import javax.annotation.PostConstruct

/**
 * Framework implementation class responsible for finding and configuring data loaders.
 */
class DgsDataLoaderProvider(private val applicationContext: ApplicationContext) {

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
        batchLoaders.forEach { dataLoaderRegistry.register(it.second.name, createDataLoader(it.first, it.second, dataLoaderRegistry)) }
        mappedBatchLoaders.forEach {
            dataLoaderRegistry.register(
                it.second.name,
                createDataLoader(it.first, it.second, dataLoaderRegistry)
            )
        }
        batchLoadersWithContext.forEach {
            dataLoaderRegistry.register(
                it.second.name,
                createDataLoader(it.first, it.second, contextSupplier, dataLoaderRegistry)
            )
        }
        mappedBatchLoadersWithContext.forEach {
            dataLoaderRegistry.register(
                it.second.name,
                createDataLoader(it.first, it.second, contextSupplier, dataLoaderRegistry)
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
            val javaClass = AopUtils.getTargetClass(dgsComponent)

            javaClass.declaredFields.asSequence().filter { it.isAnnotationPresent(DgsDataLoader::class.java) }
                .forEach { field ->
                    if (AopUtils.isAopProxy(dgsComponent)) {
                        throw UnsupportedSecuredDataLoaderException(dgsComponent::class.java)
                    }

                    val annotation = field.getAnnotation(DgsDataLoader::class.java)
                    ReflectionUtils.makeAccessible(field)

                    when (val get = field.get(dgsComponent)) {
                        is BatchLoader<*, *> ->
                            batchLoaders.add(get to annotation)
                        is BatchLoaderWithContext<*, *> ->
                            batchLoadersWithContext.add(get to annotation)
                        is MappedBatchLoader<*, *> ->
                            mappedBatchLoaders.add(get to annotation)
                        is MappedBatchLoaderWithContext<*, *> ->
                            mappedBatchLoadersWithContext.add(get to annotation)
                        else -> throw InvalidDataLoaderTypeException(dgsComponent::class.java)
                    }
                }
        }
    }

    private fun addDataLoaderComponents() {
        val dataLoaders = applicationContext.getBeansWithAnnotation(DgsDataLoader::class.java)
        dataLoaders.values.forEach { dgsComponent ->
            val javaClass = AopUtils.getTargetClass(dgsComponent)
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
        dgsDataLoader: DgsDataLoader,
        dataLoaderRegistry: DataLoaderRegistry
    ): DataLoader<out Any, out Any>? {
        val options = DataLoaderOptions.newOptions()
            .setBatchingEnabled(dgsDataLoader.batching)
            .setCachingEnabled(dgsDataLoader.caching)
        if (dgsDataLoader.maxBatchSize > 0) {
            options.setMaxBatchSize(dgsDataLoader.maxBatchSize)
        }

        val extendedBatchLoader = wrappedDataLoader(batchLoader, dgsDataLoader.name)
        if (extendedBatchLoader is DgsDataLoaderRegistryConsumer) {
            extendedBatchLoader.setDataLoaderRegistry(dataLoaderRegistry)
        }

        return DataLoader.newDataLoader(extendedBatchLoader, options)
    }

    private fun createDataLoader(
        batchLoader: MappedBatchLoader<*, *>,
        dgsDataLoader: DgsDataLoader,
        dataLoaderRegistry: DataLoaderRegistry
    ): DataLoader<out Any, out Any>? {
        val options = DataLoaderOptions.newOptions()
            .setBatchingEnabled(dgsDataLoader.batching)
            .setCachingEnabled(dgsDataLoader.caching)
        if (dgsDataLoader.maxBatchSize > 0) {
            options.setMaxBatchSize(dgsDataLoader.maxBatchSize)
        }

        val extendedBatchLoader = wrappedDataLoader(batchLoader, dgsDataLoader.name)
        if (extendedBatchLoader is DgsDataLoaderRegistryConsumer) {
            extendedBatchLoader.setDataLoaderRegistry(dataLoaderRegistry)
        }

        return DataLoader.newMappedDataLoader(extendedBatchLoader, options)
    }

    private fun <T> createDataLoader(
        batchLoader: BatchLoaderWithContext<*, *>,
        dgsDataLoader: DgsDataLoader,
        supplier: Supplier<T>,
        dataLoaderRegistry: DataLoaderRegistry
    ): DataLoader<out Any, out Any>? {
        val options = DataLoaderOptions.newOptions()
            .setBatchingEnabled(dgsDataLoader.batching)
            .setBatchLoaderContextProvider(supplier::get)
            .setCachingEnabled(dgsDataLoader.caching)

        if (dgsDataLoader.maxBatchSize > 0) {
            options.setMaxBatchSize(dgsDataLoader.maxBatchSize)
        }

        val extendedBatchLoader = wrappedDataLoader(batchLoader, dgsDataLoader.name)
        if (extendedBatchLoader is DgsDataLoaderRegistryConsumer) {
            extendedBatchLoader.setDataLoaderRegistry(dataLoaderRegistry)
        }

        return DataLoader.newDataLoader(extendedBatchLoader, options)
    }

    private fun <T> createDataLoader(
        batchLoader: MappedBatchLoaderWithContext<*, *>,
        dgsDataLoader: DgsDataLoader,
        supplier: Supplier<T>,
        dataLoaderRegistry: DataLoaderRegistry
    ): DataLoader<out Any, out Any>? {
        val options = DataLoaderOptions.newOptions()
            .setBatchingEnabled(dgsDataLoader.batching)
            .setBatchLoaderContextProvider(supplier::get)
            .setCachingEnabled(dgsDataLoader.caching)

        if (dgsDataLoader.maxBatchSize > 0) {
            options.setMaxBatchSize(dgsDataLoader.maxBatchSize)
        }

        val extendedBatchLoader = wrappedDataLoader(batchLoader, dgsDataLoader.name)
        if (extendedBatchLoader is DgsDataLoaderRegistryConsumer) {
            extendedBatchLoader.setDataLoaderRegistry(dataLoaderRegistry)
        }

        return DataLoader.newMappedDataLoader(extendedBatchLoader, options)
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

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(DgsDataLoaderProvider::class.java)
    }
}
