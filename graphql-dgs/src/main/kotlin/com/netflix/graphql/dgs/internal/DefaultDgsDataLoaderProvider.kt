/*
 * Copyright 2025 Netflix, Inc.
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
import com.netflix.graphql.dgs.DgsDataLoaderCustomizer
import com.netflix.graphql.dgs.DgsDataLoaderOptionsProvider
import com.netflix.graphql.dgs.DgsDataLoaderRegistryConsumer
import com.netflix.graphql.dgs.DgsDispatchPredicate
import com.netflix.graphql.dgs.exceptions.DgsUnnamedDataLoaderOnFieldException
import com.netflix.graphql.dgs.exceptions.InvalidDataLoaderTypeException
import com.netflix.graphql.dgs.exceptions.MultipleDataLoadersDefinedException
import com.netflix.graphql.dgs.exceptions.UnsupportedSecuredDataLoaderException
import com.netflix.graphql.dgs.internal.utils.DataLoaderNameUtil
import jakarta.annotation.PostConstruct
import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderWithContext
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import org.dataloader.registries.DispatchPredicate
import org.dataloader.registries.ScheduledDataLoaderRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.type.StandardMethodMetadata
import org.springframework.util.ReflectionUtils
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.function.Supplier
import kotlin.system.measureTimeMillis

/**
 * Framework implementation class responsible for finding and configuring data loaders.
 */
class DefaultDgsDataLoaderProvider(
    private val applicationContext: ApplicationContext,
    private val extensionProviders: List<DataLoaderInstrumentationExtensionProvider> = listOf(),
    private val customizers: List<DgsDataLoaderCustomizer> = listOf(),
    private val dataLoaderOptionsProvider: DgsDataLoaderOptionsProvider = DefaultDataLoaderOptionsProvider(),
    private val scheduledExecutorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val scheduleDuration: Duration = Duration.ofMillis(10),
    private val enableTickerMode: Boolean = false,
) : DgsDataLoaderProvider {
    private data class LoaderHolder<T>(
        val theLoader: T,
        val annotation: DgsDataLoader,
        val name: String,
        val dispatchPredicate: DispatchPredicate? = null,
    )

    private val dataLoaders = mutableMapOf<String, Class<*>>()
    private val batchLoaders = mutableListOf<LoaderHolder<BatchLoader<*, *>>>()
    private val batchLoadersWithContext = mutableListOf<LoaderHolder<BatchLoaderWithContext<*, *>>>()
    private val mappedBatchLoaders = mutableListOf<LoaderHolder<MappedBatchLoader<*, *>>>()
    private val mappedBatchLoadersWithContext = mutableListOf<LoaderHolder<MappedBatchLoaderWithContext<*, *>>>()

    override fun buildRegistry(): DataLoaderRegistry = buildRegistryWithContextSupplier { null }

    override fun <T> buildRegistryWithContextSupplier(contextSupplier: Supplier<T>): DataLoaderRegistry {
        // We need to set the default predicate to 20ms and individually override with DISPATCH_ALWAYS or the custom dispatch predicate, if specified
        // The data loader ends up applying the overall dispatch predicate when the custom dispatch predicate is not true otherwise.
        val registry =
            ScheduledDataLoaderRegistry
                .newScheduledRegistry()
                .scheduledExecutorService(
                    scheduledExecutorService,
                ).tickerMode(enableTickerMode)
                .schedule(scheduleDuration)
                .dispatchPredicate(DispatchPredicate.DISPATCH_NEVER)
                .build()

        val totalTime =
            measureTimeMillis {
                batchLoaders.forEach { registerDataLoader(it, registry, contextSupplier, extensionProviders) }
                batchLoadersWithContext.forEach { registerDataLoader(it, registry, contextSupplier, extensionProviders) }
                mappedBatchLoaders.forEach { registerDataLoader(it, registry, contextSupplier, extensionProviders) }
                mappedBatchLoadersWithContext.forEach { registerDataLoader(it, registry, contextSupplier, extensionProviders) }
            }
        logger.debug("Created DGS dataloader registry in {}ms", totalTime)
        return registry
    }

    @PostConstruct
    internal fun findDataLoaders() {
        addDataLoaderComponents()
        addDataLoaderFields()
    }

    private fun addDataLoaderFields() {
        val dataLoaders = applicationContext.getBeansWithAnnotation<DgsComponent>()
        dataLoaders.values.forEach { dgsComponent ->
            val javaClass = AopUtils.getTargetClass(dgsComponent)

            javaClass.declaredFields
                .asSequence()
                .filter { it.isAnnotationPresent(DgsDataLoader::class.java) }
                .forEach { field ->
                    if (AopUtils.isAopProxy(dgsComponent)) {
                        throw UnsupportedSecuredDataLoaderException(dgsComponent::class.java)
                    }

                    val annotation = field.getAnnotation(DgsDataLoader::class.java)
                    ReflectionUtils.makeAccessible(field)

                    if (annotation.name == DgsDataLoader.GENERATE_DATA_LOADER_NAME) {
                        throw DgsUnnamedDataLoaderOnFieldException(field)
                    }

                    addDataLoader(field.get(dgsComponent), annotation.name, dgsComponent::class.java, annotation)
                }
        }
    }

    private fun addDataLoaderComponents() {
        val dataLoaders = applicationContext.getBeansWithAnnotation<DgsDataLoader>()
        dataLoaders.forEach { (beanName, beanInstance) ->
            val javaClass = AopUtils.getTargetClass(beanInstance)

            // check for class-level annotations
            val annotation = javaClass.getAnnotation(DgsDataLoader::class.java)
            if (annotation != null) {
                val dataLoaderName = DataLoaderNameUtil.getDataLoaderName(javaClass, annotation)
                val predicateField = javaClass.declaredFields.find { it.isAnnotationPresent(DgsDispatchPredicate::class.java) }
                if (predicateField != null) {
                    ReflectionUtils.makeAccessible(predicateField)
                    val dispatchPredicate = predicateField.get(beanInstance)
                    if (dispatchPredicate is DispatchPredicate) {
                        addDataLoader(beanInstance, dataLoaderName, javaClass, annotation, dispatchPredicate)
                    }
                } else {
                    addDataLoader(beanInstance, dataLoaderName, javaClass, annotation)
                }
            } else {
                // Check for method-level bean annotations in configuration classes
                if (applicationContext is ConfigurableApplicationContext) {
                    val beanDefinition = applicationContext.beanFactory.getBeanDefinition(beanName)
                    if (beanDefinition.source is StandardMethodMetadata) {
                        val methodMetadata = beanDefinition.source as StandardMethodMetadata
                        val method = methodMetadata.introspectedMethod
                        val methodAnnotation = method.getAnnotation(DgsDataLoader::class.java)
                        if (methodAnnotation != null) {
                            val dataLoaderName = DataLoaderNameUtil.getDataLoaderName(javaClass, methodAnnotation)
                            addDataLoader(beanInstance, dataLoaderName, javaClass, methodAnnotation, null)
                        }
                    }
                }
            }
        }
    }

    private fun <T : Any> addDataLoader(
        dataLoader: T,
        dataLoaderName: String,
        dgsComponentClass: Class<*>,
        annotation: DgsDataLoader,
        dispatchPredicate: DispatchPredicate? = null,
    ) {
        if (dataLoaders.contains(dataLoaderName)) {
            throw MultipleDataLoadersDefinedException(dgsComponentClass, dataLoaders.getValue(dataLoaderName))
        }
        dataLoaders[dataLoaderName] = dgsComponentClass

        fun <T : Any> createHolder(t: T): LoaderHolder<T> = LoaderHolder(t, annotation, dataLoaderName, dispatchPredicate)

        when (val customizedDataLoader = runCustomizers(dataLoader, dataLoaderName, dgsComponentClass)) {
            is BatchLoader<*, *> -> batchLoaders.add(createHolder(customizedDataLoader))
            is BatchLoaderWithContext<*, *> -> batchLoadersWithContext.add(createHolder(customizedDataLoader))
            is MappedBatchLoader<*, *> -> mappedBatchLoaders.add(createHolder(customizedDataLoader))
            is MappedBatchLoaderWithContext<*, *> -> mappedBatchLoadersWithContext.add(createHolder(customizedDataLoader))
            else -> throw InvalidDataLoaderTypeException(dgsComponentClass)
        }
    }

    private fun runCustomizers(
        originalDataLoader: Any,
        name: String,
        dgsComponentClass: Class<*>,
    ): Any {
        var dataLoader = originalDataLoader

        customizers.forEach {
            dataLoader =
                when (dataLoader) {
                    is BatchLoader<*, *> -> it.provide(dataLoader as BatchLoader<*, *>, name)
                    is BatchLoaderWithContext<*, *> -> it.provide(dataLoader as BatchLoaderWithContext<*, *>, name)
                    is MappedBatchLoader<*, *> -> it.provide(dataLoader as MappedBatchLoader<*, *>, name)
                    is MappedBatchLoaderWithContext<*, *> -> it.provide(dataLoader as MappedBatchLoaderWithContext<*, *>, name)
                    else -> throw InvalidDataLoaderTypeException(dgsComponentClass)
                }
        }

        return dataLoader
    }

    private fun createDataLoader(
        batchLoader: BatchLoader<*, *>,
        dgsDataLoader: DgsDataLoader,
        dataLoaderName: String,
        dataLoaderRegistry: DataLoaderRegistry,
        extensionProviders: Iterable<DataLoaderInstrumentationExtensionProvider>,
    ): DataLoader<*, *> {
        val options = dataLoaderOptionsProvider.getOptions(dataLoaderName, dgsDataLoader)

        if (batchLoader is DgsDataLoaderRegistryConsumer) {
            batchLoader.setDataLoaderRegistry(dataLoaderRegistry)
        }

        val extendedBatchLoader = wrappedDataLoader(batchLoader, dataLoaderName, extensionProviders)
        return DataLoaderFactory.newDataLoader(dataLoaderName, extendedBatchLoader, options.build())
    }

    private fun createDataLoader(
        batchLoader: MappedBatchLoader<*, *>,
        dgsDataLoader: DgsDataLoader,
        dataLoaderName: String,
        dataLoaderRegistry: DataLoaderRegistry,
        extensionProviders: Iterable<DataLoaderInstrumentationExtensionProvider>,
    ): DataLoader<*, *> {
        val options = dataLoaderOptionsProvider.getOptions(dataLoaderName, dgsDataLoader)

        if (batchLoader is DgsDataLoaderRegistryConsumer) {
            batchLoader.setDataLoaderRegistry(dataLoaderRegistry)
        }
        val extendedBatchLoader = wrappedDataLoader(batchLoader, dataLoaderName, extensionProviders)

        return DataLoaderFactory.newMappedDataLoader(extendedBatchLoader, options.build())
    }

    private fun <T : Any> createDataLoader(
        batchLoader: BatchLoaderWithContext<*, *>,
        dgsDataLoader: DgsDataLoader,
        dataLoaderName: String,
        supplier: Supplier<T>,
        dataLoaderRegistry: DataLoaderRegistry,
        extensionProviders: Iterable<DataLoaderInstrumentationExtensionProvider>,
    ): DataLoader<*, *> {
        val options =
            dataLoaderOptionsProvider
                .getOptions(dataLoaderName, dgsDataLoader)
                .setBatchLoaderContextProvider(supplier::get)

        if (batchLoader is DgsDataLoaderRegistryConsumer) {
            batchLoader.setDataLoaderRegistry(dataLoaderRegistry)
        }

        val extendedBatchLoader = wrappedDataLoader(batchLoader, dataLoaderName, extensionProviders)
        return DataLoaderFactory.newDataLoader(dataLoaderName, extendedBatchLoader, options.build())
    }

    private fun <T : Any> createDataLoader(
        batchLoader: MappedBatchLoaderWithContext<*, *>,
        dgsDataLoader: DgsDataLoader,
        dataLoaderName: String,
        supplier: Supplier<T>,
        dataLoaderRegistry: DataLoaderRegistry,
        extensionProviders: Iterable<DataLoaderInstrumentationExtensionProvider>,
    ): DataLoader<*, *> {
        val options =
            dataLoaderOptionsProvider
                .getOptions(dataLoaderName, dgsDataLoader)
                .setBatchLoaderContextProvider(supplier::get)

        if (batchLoader is DgsDataLoaderRegistryConsumer) {
            batchLoader.setDataLoaderRegistry(dataLoaderRegistry)
        }

        val extendedBatchLoader = wrappedDataLoader(batchLoader, dataLoaderName, extensionProviders)
        return DataLoaderFactory.newMappedDataLoader(dataLoaderName, extendedBatchLoader, options.build())
    }

    private fun registerDataLoader(
        holder: LoaderHolder<*>,
        registry: ScheduledDataLoaderRegistry,
        contextSupplier: Supplier<*>,
        extensionProviders: Iterable<DataLoaderInstrumentationExtensionProvider>,
    ) {
        val loader =
            when (holder.theLoader) {
                is BatchLoader<*, *> ->
                    createDataLoader(
                        holder.theLoader,
                        holder.annotation,
                        holder.name,
                        registry,
                        extensionProviders,
                    )

                is BatchLoaderWithContext<*, *> ->
                    createDataLoader(
                        holder.theLoader,
                        holder.annotation,
                        holder.name,
                        contextSupplier,
                        registry,
                        extensionProviders,
                    )

                is MappedBatchLoader<*, *> ->
                    createDataLoader(
                        holder.theLoader,
                        holder.annotation,
                        holder.name,
                        registry,
                        extensionProviders,
                    )

                is MappedBatchLoaderWithContext<*, *> ->
                    createDataLoader(
                        holder.theLoader,
                        holder.annotation,
                        holder.name,
                        contextSupplier,
                        registry,
                        extensionProviders,
                    )

                else -> throw IllegalArgumentException("Data loader ${holder.name} has unknown type")
            }
        // detect and throw an exception if multiple data loaders use the same name
        if (registry.keys.contains(holder.name)) {
            throw MultipleDataLoadersDefinedException(holder.theLoader.javaClass)
        }

        if (holder.dispatchPredicate == null) {
            registry.register(holder.name, loader, DispatchPredicate.DISPATCH_ALWAYS)
        } else {
            registry.register(holder.name, loader, holder.dispatchPredicate)
        }
    }

    private inline fun <reified T> wrappedDataLoader(
        loader: T,
        name: String,
        extensionProviders: Iterable<DataLoaderInstrumentationExtensionProvider>,
    ): T {
        try {
            when (loader) {
                is BatchLoader<*, *> -> {
                    var wrappedBatchLoader: BatchLoader<*, *> = loader
                    extensionProviders.forEach { wrappedBatchLoader = it.provide(wrappedBatchLoader, name) }
                    return wrappedBatchLoader as T
                }
                is BatchLoaderWithContext<*, *> -> {
                    var wrappedBatchLoader: BatchLoaderWithContext<*, *> = loader
                    extensionProviders.forEach { wrappedBatchLoader = it.provide(wrappedBatchLoader, name) }
                    return wrappedBatchLoader as T
                }
                is MappedBatchLoader<*, *> -> {
                    var wrappedBatchLoader: MappedBatchLoader<*, *> = loader
                    extensionProviders.forEach { wrappedBatchLoader = it.provide(wrappedBatchLoader, name) }
                    return wrappedBatchLoader as T
                }
                is MappedBatchLoaderWithContext<*, *> -> {
                    var wrappedBatchLoader: MappedBatchLoaderWithContext<*, *> = loader
                    extensionProviders.forEach { wrappedBatchLoader = it.provide(wrappedBatchLoader, name) }
                    return wrappedBatchLoader as T
                }
            }
        } catch (ex: NoSuchBeanDefinitionException) {
            logger.debug("Unable to wrap the [{} : {}]", name, loader, ex)
        }
        return loader
    }

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(DefaultDgsDataLoaderProvider::class.java)
    }
}
