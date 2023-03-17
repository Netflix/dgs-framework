/*
 * Copyright 2022 Netflix, Inc.
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
import com.netflix.graphql.dgs.DgsDispatchPredicate
import com.netflix.graphql.dgs.exceptions.DgsUnnamedDataLoaderOnFieldException
import com.netflix.graphql.dgs.exceptions.InvalidDataLoaderTypeException
import com.netflix.graphql.dgs.exceptions.UnsupportedSecuredDataLoaderException
import com.netflix.graphql.dgs.internal.utils.DataLoaderNameUtil
import jakarta.annotation.PostConstruct
import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderWithContext
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import org.dataloader.DataLoaderRegistry
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import org.dataloader.registries.DispatchPredicate
import org.dataloader.registries.ScheduledDataLoaderRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.ApplicationContext
import org.springframework.util.ReflectionUtils
import java.lang.reflect.Modifier
import java.util.function.Supplier

/**
 * Framework implementation class responsible for finding and configuring data loaders.
 */
class DgsDataLoaderProvider(private val applicationContext: ApplicationContext) {

    private data class LoaderHolder<T>(val theLoader: T, val annotation: DgsDataLoader, val name: String, val dispatchPredicate: DispatchPredicate? = null)

    private val batchLoaders = mutableListOf<LoaderHolder<BatchLoader<*, *>>>()
    private val batchLoadersWithContext = mutableListOf<LoaderHolder<BatchLoaderWithContext<*, *>>>()
    private val mappedBatchLoaders = mutableListOf<LoaderHolder<MappedBatchLoader<*, *>>>()
    private val mappedBatchLoadersWithContext = mutableListOf<LoaderHolder<MappedBatchLoaderWithContext<*, *>>>()

    fun buildRegistry(): DataLoaderRegistry {
        return buildRegistryWithContextSupplier { null }
    }

    fun <T> buildRegistryWithContextSupplier(contextSupplier: Supplier<T>): DataLoaderRegistry {
        val startTime = System.currentTimeMillis()
        //val dataLoaderRegistry = DataLoaderRegistry()
        val dgsDataLoaderRegistry = DgsDataLoaderRegistry()

        batchLoaders.forEach {
            if (it.dispatchPredicate == null) {
                dgsDataLoaderRegistry.register(
                    it.name,
                    createDataLoader(it.theLoader, it.annotation, it.name, dgsDataLoaderRegistry)
                )
            } else {
                dgsDataLoaderRegistry.registerWithDispatchPredicate(it.name, createDataLoader(it.theLoader, it.annotation, it.name, dgsDataLoaderRegistry), it.dispatchPredicate)
            }
        }
        mappedBatchLoaders.forEach {
            if (it.dispatchPredicate == null) {
                dgsDataLoaderRegistry.register(
                    it.name,
                    createDataLoader(it.theLoader, it.annotation, it.name, dgsDataLoaderRegistry)
                )
            } else {
                dgsDataLoaderRegistry.registerWithDispatchPredicate(
                    it.name,
                    createDataLoader(it.theLoader, it.annotation, it.name, dgsDataLoaderRegistry),
                    it.dispatchPredicate
                )
            }
        }
        batchLoadersWithContext.forEach {
            if (it.dispatchPredicate == null) {
                dgsDataLoaderRegistry.register(
                    it.name,
                    createDataLoader(it.theLoader, it.annotation, it.name, contextSupplier, dgsDataLoaderRegistry)
                )
            } else {
                dgsDataLoaderRegistry.registerWithDispatchPredicate(
                    it.name,
                    createDataLoader(it.theLoader, it.annotation, it.name, contextSupplier, dgsDataLoaderRegistry),
                    it.dispatchPredicate
                )
            }
        }
        mappedBatchLoadersWithContext.forEach {
            if (it.dispatchPredicate == null) {
                dgsDataLoaderRegistry.register(
                    it.name,
                    createDataLoader(it.theLoader, it.annotation, it.name, contextSupplier, dgsDataLoaderRegistry)
                )
            } else {
                dgsDataLoaderRegistry.registerWithDispatchPredicate(
                    it.name,
                    createDataLoader(it.theLoader, it.annotation, it.name, contextSupplier, dgsDataLoaderRegistry),
                    it.dispatchPredicate
                )
            }
        }

        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        logger.debug("Created DGS dataloader registry in {}ms", totalTime)

        return dgsDataLoaderRegistry
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

                    if (annotation.name == DgsDataLoader.GENERATE_DATA_LOADER_NAME) {
                        throw DgsUnnamedDataLoaderOnFieldException(field)
                    }

                    fun <T : Any> createHolder(t: T): LoaderHolder<T> = LoaderHolder(t, annotation, annotation.name)
                    when (val get = field.get(dgsComponent)) {
                        is BatchLoader<*, *> -> batchLoaders.add(createHolder(get))
                        is BatchLoaderWithContext<*, *> -> batchLoadersWithContext.add(createHolder(get))
                        is MappedBatchLoader<*, *> -> mappedBatchLoaders.add(createHolder(get))
                        is MappedBatchLoaderWithContext<*, *> -> mappedBatchLoadersWithContext.add(createHolder(get))
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
            val predicateField = javaClass.declaredFields.asSequence().find { it.isAnnotationPresent(DgsDispatchPredicate::class.java) }
            if (predicateField != null ) {
                    ReflectionUtils.makeAccessible(predicateField)
                    val dispatchPredicate =  predicateField.get(dgsComponent)
                    if (dispatchPredicate is DispatchPredicate) {
                       addDataLoaders(dgsComponent, javaClass, annotation, dispatchPredicate)
                    }
            } else {
                addDataLoaders(dgsComponent, javaClass, annotation, null)
            }
        }
    }

    private fun <T: Any>addDataLoaders(dgsComponent: T, targetClass: Class<*>, annotation: DgsDataLoader, dispatchPredicate: DispatchPredicate?) {
        fun <T : Any> createHolder(t: T): LoaderHolder<T> =
            LoaderHolder(t, annotation, DataLoaderNameUtil.getDataLoaderName(targetClass, annotation), dispatchPredicate)
        when (dgsComponent) {
            is BatchLoader<*, *> -> batchLoaders.add(createHolder(dgsComponent))
            is BatchLoaderWithContext<*, *> -> batchLoadersWithContext.add(createHolder(dgsComponent))
            is MappedBatchLoader<*, *> -> mappedBatchLoaders.add(createHolder(dgsComponent))
            is MappedBatchLoaderWithContext<*, *> -> mappedBatchLoadersWithContext.add(createHolder(dgsComponent))
            else -> throw InvalidDataLoaderTypeException(dgsComponent::class.java)
        }
    }

    private fun createDataLoader(
        batchLoader: BatchLoader<*, *>,
        dgsDataLoader: DgsDataLoader,
        dataLoaderName: String,
        dataLoaderRegistry: DataLoaderRegistry
    ): DataLoader<*, *> {
        val options = dataLoaderOptions(dgsDataLoader)

        if (batchLoader is DgsDataLoaderRegistryConsumer) {
            batchLoader.setDataLoaderRegistry(dataLoaderRegistry)
        }

        val extendedBatchLoader = wrappedDataLoader(batchLoader, dataLoaderName)
        return DataLoaderFactory.newDataLoader(extendedBatchLoader, options)
    }

    private fun createDataLoader(
        batchLoader: MappedBatchLoader<*, *>,
        dgsDataLoader: DgsDataLoader,
        dataLoaderName: String,
        dataLoaderRegistry: DataLoaderRegistry
    ): DataLoader<*, *> {
        val options = dataLoaderOptions(dgsDataLoader)

        if (batchLoader is DgsDataLoaderRegistryConsumer) {
            batchLoader.setDataLoaderRegistry(dataLoaderRegistry)
        }
        val extendedBatchLoader = wrappedDataLoader(batchLoader, dataLoaderName)

        return DataLoaderFactory.newMappedDataLoader(extendedBatchLoader, options)
    }

    private fun <T> createDataLoader(
        batchLoader: BatchLoaderWithContext<*, *>,
        dgsDataLoader: DgsDataLoader,
        dataLoaderName: String,
        supplier: Supplier<T>,
        dataLoaderRegistry: DataLoaderRegistry
    ): DataLoader<*, *> {
        val options = dataLoaderOptions(dgsDataLoader)
            .setBatchLoaderContextProvider(supplier::get)

        if (batchLoader is DgsDataLoaderRegistryConsumer) {
            batchLoader.setDataLoaderRegistry(dataLoaderRegistry)
        }

        val extendedBatchLoader = wrappedDataLoader(batchLoader, dataLoaderName)
        return DataLoaderFactory.newDataLoader(extendedBatchLoader, options)
    }

    private fun <T> createDataLoader(
        batchLoader: MappedBatchLoaderWithContext<*, *>,
        dgsDataLoader: DgsDataLoader,
        dataLoaderName: String,
        supplier: Supplier<T>,
        dataLoaderRegistry: DataLoaderRegistry
    ): DataLoader<*, *> {
        val options = dataLoaderOptions(dgsDataLoader)
            .setBatchLoaderContextProvider(supplier::get)

        if (batchLoader is DgsDataLoaderRegistryConsumer) {
            batchLoader.setDataLoaderRegistry(dataLoaderRegistry)
        }

        val extendedBatchLoader = wrappedDataLoader(batchLoader, dataLoaderName)
        return DataLoaderFactory.newMappedDataLoader(extendedBatchLoader, options)
    }

    private fun dataLoaderOptions(annotation: DgsDataLoader): DataLoaderOptions {
        val options = DataLoaderOptions()
            .setBatchingEnabled(annotation.batching)
            .setCachingEnabled(annotation.caching)
        if (annotation.maxBatchSize > 0) {
            options.setMaxBatchSize(annotation.maxBatchSize)
        }
        return options
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
