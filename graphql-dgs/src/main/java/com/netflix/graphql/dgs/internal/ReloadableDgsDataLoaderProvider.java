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

package com.netflix.graphql.dgs.internal;

import com.netflix.graphql.dgs.DataLoaderInstrumentationExtensionProvider;
import com.netflix.graphql.dgs.DgsDataLoaderCustomizer;
import com.netflix.graphql.dgs.DgsDataLoaderOptionsProvider;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * Implementation of a {@link DgsDataLoaderProvider} that supports data loader reloading.
 * This is done by creating a new {@link DefaultDgsDataLoaderProvider} when {@link #forceReload} is called.
 *
 * <p>Reloading by passing an {@link ApplicationContext} to {@link #forceReload} will set the application only if
 * <em>force reloading</em> is successful. If <em>force reloading</em> is not successful, the provider and the
 * <em>application</em> context will stay the same.
 */
public class ReloadableDgsDataLoaderProvider implements DgsDataLoaderProvider {
    private static final Logger logger = LoggerFactory.getLogger(ReloadableDgsDataLoaderProvider.class);

    private volatile ApplicationContext applicationContext;
    private final ScheduledExecutorService scheduledExecutorService;
    private final List<DataLoaderInstrumentationExtensionProvider> extensionProviders;
    private final List<DgsDataLoaderCustomizer> customizers;
    private final DgsDataLoaderOptionsProvider dataLoaderOptionsProvider;
    private final Duration scheduleDuration;
    private final boolean enableTickerMode;

    private volatile DefaultDgsDataLoaderProvider currentProvider;
    private volatile Instant lastReloadTime;

    /**
     * @param applicationContext The Spring application context for bean discovery
     * @param scheduledExecutorService Executor service for scheduled data loader registry
     * @param extensionProviders List of data loader instrumentation extension providers
     * @param customizers List of data loader customizers
     * @param dataLoaderOptionsProvider Provider for data loader options
     * @param scheduleDuration Duration for data loader scheduling
     * @param enableTickerMode Whether to enable ticker mode for the registry
     */
    public ReloadableDgsDataLoaderProvider(
            ApplicationContext applicationContext,
            ScheduledExecutorService scheduledExecutorService,
            List<DataLoaderInstrumentationExtensionProvider> extensionProviders,
            List<DgsDataLoaderCustomizer> customizers,
            DgsDataLoaderOptionsProvider dataLoaderOptionsProvider,
            Duration scheduleDuration,
            boolean enableTickerMode) {
        this.applicationContext = applicationContext;
        this.scheduledExecutorService = scheduledExecutorService;
        this.extensionProviders = extensionProviders;
        this.customizers = customizers;
        this.dataLoaderOptionsProvider = dataLoaderOptionsProvider;
        this.scheduleDuration = scheduleDuration;
        this.enableTickerMode = enableTickerMode;
    }

    public ReloadableDgsDataLoaderProvider(
            ApplicationContext applicationContext, ScheduledExecutorService scheduledExecutorService) {
        this(
                applicationContext,
                scheduledExecutorService,
                List.of(),
                List.of(),
                new DefaultDataLoaderOptionsProvider(),
                Duration.ofMillis(10),
                false);
    }

    /**
     * Gets the timestamp of the last data loader reload.
     *
     * @return Instant of last reload, or null if never reloaded
     */
    public Instant getLastReloadTime() {
        return lastReloadTime;
    }

    /**
     * Checks if this provider has been initialized (i.e., has a current provider).
     *
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return currentProvider != null;
    }

    @Override
    public DataLoaderRegistry buildRegistry() {
        return getOrCreateProvider().buildRegistry();
    }

    @Override
    public <T> DataLoaderRegistry buildRegistryWithContextSupplier(Supplier<T> contextSupplier) {
        return getOrCreateProvider().buildRegistryWithContextSupplier(contextSupplier);
    }

    /**
     * Gets the current provider or creates a new one if reload is indicated.
     * This method is thread-safe and uses the double-checked locking pattern.
     */
    private DefaultDgsDataLoaderProvider getOrCreateProvider() {
        if (currentProvider == null) {
            synchronized (this) {
                if (currentProvider == null) {
                    logger.info("Reloading data loaders due to indicator signal");
                    currentProvider = createNewProvider(applicationContext);
                    lastReloadTime = Instant.now();
                }
            }
        }
        return currentProvider;
    }

    /** Creates a new DgsDataLoaderProvider instance and triggers data loader discovery. */
    private DefaultDgsDataLoaderProvider createNewProvider(ApplicationContext applicationContext) {
        DefaultDgsDataLoaderProvider newProvider = new DefaultDgsDataLoaderProvider(
                applicationContext,
                extensionProviders,
                customizers,
                dataLoaderOptionsProvider,
                scheduledExecutorService,
                scheduleDuration,
                enableTickerMode);
        // Trigger discovery of data loaders
        newProvider.findDataLoaders();
        return newProvider;
    }

    /**
     * Programmatic API to force data loader reload.
     * Useful for development tools and administrative interfaces.
     *
     * @return true if reload was successful, false otherwise
     */
    public boolean forceReload() {
        return forceReload(applicationContext);
    }

    /**
     * Programmatic API to force data loader reload.
     * Useful for development tools and administrative interfaces.
     *
     * @return true if reload was successful, false otherwise
     */
    public boolean forceReload(ApplicationContext applicationContext) {
        try {
            logger.info(
                    "Forcing reload data loaders for application context {},{}",
                    applicationContext.getId(),
                    applicationContext.getApplicationName());
            synchronized (this) {
                this.currentProvider = createNewProvider(applicationContext);
                this.applicationContext = applicationContext;
                this.lastReloadTime = Instant.now();
            }
            logger.debug("Reload application context successfully");
            return true;
        } catch (Exception e) {
            logger.error("Failed to force reload of data loaders", e);
            return false;
        }
    }
}
