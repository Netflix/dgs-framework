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
import com.netflix.graphql.dgs.DgsDataLoaderCustomizer
import com.netflix.graphql.dgs.DgsDataLoaderOptionsProvider
import org.dataloader.DataLoaderRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledExecutorService
import java.util.function.Supplier

/**
 * Implementation of a [DgsDataLoaderProvider] that supports data loader reloading.
 * This is done by creating a new [DefaultDgsDataLoaderProvider] when [forceReload] is called.
 *
 * Reloading by passing an [ApplicationContext] to [forceReload] will set the application only if _force reloading_ is successful.
 * If _force reloading_ is not successful, the provider and the _application_ context will stay the same.
 *
 * @param applicationContext The Spring application context for bean discovery
 * @param extensionProviders List of data loader instrumentation extension providers
 * @param customizers List of data loader customizers
 * @param dataLoaderOptionsProvider Provider for data loader options
 * @param scheduledExecutorService Executor service for scheduled data loader registry
 * @param scheduleDuration Duration for data loader scheduling
 * @param enableTickerMode Whether to enable ticker mode for the registry
 */
class ReloadableDgsDataLoaderProvider(
    @Volatile private var applicationContext: ApplicationContext,
    private val scheduledExecutorService: ScheduledExecutorService,
    private val extensionProviders: List<DataLoaderInstrumentationExtensionProvider> = listOf(),
    private val customizers: List<DgsDataLoaderCustomizer> = listOf(),
    private val dataLoaderOptionsProvider: DgsDataLoaderOptionsProvider = DefaultDataLoaderOptionsProvider(),
    private val scheduleDuration: Duration = Duration.ofMillis(10),
    private val enableTickerMode: Boolean = false,
) : DgsDataLoaderProvider {
    @Volatile
    private var currentProvider: DefaultDgsDataLoaderProvider? = null

    @Volatile
    private var lastReloadTime: Instant? = null

    /**
     * Gets the timestamp of the last data loader reload.
     *
     * @return Instant of last reload, or null if never reloaded
     */
    fun getLastReloadTime(): Instant? = lastReloadTime

    /**
     * Checks if this provider has been initialized (i.e., has a current provider).
     *
     * @return true if initialized, false otherwise
     */
    fun isInitialized(): Boolean = currentProvider != null

    /**
     * Builds a data loader registry, checking for reload signal first.
     *
     * @return DataLoaderRegistry instance with all discovered data loaders
     */
    override fun buildRegistry(): DataLoaderRegistry {
        val provider = getOrCreateProvider()
        return provider.buildRegistry()
    }

    /**
     * Builds a data loader registry with a context supplier, checking for reload signal first.
     *
     * @param contextSupplier Supplier for GraphQL context
     * @return DataLoaderRegistry instance with all discovered data loaders
     */
    override fun <T> buildRegistryWithContextSupplier(contextSupplier: Supplier<T>): DataLoaderRegistry {
        val provider = getOrCreateProvider()
        return provider.buildRegistryWithContextSupplier(contextSupplier)
    }

    /**
     * Gets the current provider or creates a new one if reload is indicated.
     * This method is thread-safe and uses double-checked locking pattern.
     *
     * @return Current or newly created DgsDataLoaderProvider
     */
    private fun getOrCreateProvider(): DefaultDgsDataLoaderProvider {
        if (currentProvider == null) {
            synchronized(this) {
                if (currentProvider == null) {
                    logger.info("Reloading data loaders due to indicator signal")
                    currentProvider = createNewProvider(applicationContext)
                    lastReloadTime = Instant.now()
                }
            }
        }
        return currentProvider!!
    }

    /**
     * Creates a new DgsDataLoaderProvider instance and triggers data loader discovery.
     *
     * @return New DgsDataLoaderProvider instance with discovered data loaders
     */
    private fun createNewProvider(applicationContext: ApplicationContext): DefaultDgsDataLoaderProvider {
        val newProvider =
            DefaultDgsDataLoaderProvider(
                applicationContext = applicationContext,
                scheduledExecutorService = scheduledExecutorService,
                extensionProviders = extensionProviders,
                customizers = customizers,
                dataLoaderOptionsProvider = dataLoaderOptionsProvider,
                scheduleDuration = scheduleDuration,
                enableTickerMode = enableTickerMode,
            )
        // Trigger discovery of data loaders
        newProvider.findDataLoaders()
        return newProvider
    }

    /**
     * Programmatic API to force data loader reload.
     * Useful for development tools and administrative interfaces.
     * This method is thread-safe and will create a new provider instance.
     *
     * @return true if reload was successful, false otherwise
     */
    fun forceReload(): Boolean = forceReload(applicationContext)

    /**
     * Programmatic API to force data loader reload.
     * Useful for development tools and administrative interfaces.
     * This method is thread-safe and will create a new provider instance.
     *
     * @return true if reload was successful, false otherwise
     */
    fun forceReload(applicationContext: ApplicationContext): Boolean {
        try {
            logger.info(
                "Forcing reload data loaders for application context {},{}",
                applicationContext.id,
                applicationContext.applicationName,
            )
            synchronized(this) {
                this.currentProvider = createNewProvider(applicationContext)
                this.applicationContext = applicationContext
                this.lastReloadTime = Instant.now()
            }
            logger.debug("Reload application context successfully")
            return true
        } catch (e: Exception) {
            logger.error("Failed to force reload of data loaders", e)
            return false
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ReloadableDgsDataLoaderProvider::class.java)
    }
}
