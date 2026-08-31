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

import com.netflix.graphql.dgs.DgsDataLoaderReloadController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of DgsDataLoaderReloadController.
 *
 * <p>This controller provides programmatic access to data loader reloading functionality
 * when reload mode is enabled. It maintains statistics about reload operations and
 * provides a convenient API for development tools and administrative interfaces.
 *
 * <p>The controller is only registered as a Spring bean when reload functionality is enabled
 * via the 'dgs.reload.enabled' property.
 */
public class DefaultDgsDataLoaderReloadController implements DgsDataLoaderReloadController {
    private static final Logger logger = LoggerFactory.getLogger(DefaultDgsDataLoaderReloadController.class);

    private final ReloadableDgsDataLoaderProvider reloadableProvider;

    private volatile Instant lastReloadTime;
    private volatile Long lastReloadDuration;
    private final AtomicLong totalReloads = new AtomicLong(0);

    public DefaultDgsDataLoaderReloadController(ReloadableDgsDataLoaderProvider reloadableProvider) {
        this.reloadableProvider = reloadableProvider;
    }

    /**
     * Force immediate reload of all data loaders.
     *
     * @return true if reload was successful, false if an error occurred
     */
    @Override
    public boolean reloadDataLoaders() {
        logger.info("Programmatic data loader reload requested");

        try {
            long start = System.currentTimeMillis();
            boolean success = reloadableProvider.forceReload();
            if (!success) {
                logger.warn("Data loader reload reported failure");
                return false;
            }
            long duration = System.currentTimeMillis() - start;

            recordReload(duration);

            logger.info("Data loader reload completed successfully in {}ms", duration);
            return true;
        } catch (Exception e) {
            logger.error("Failed to reload data loaders", e);
            return false;
        }
    }

    /**
     * Force immediate reload of all data loaders defined by an explicit application context.
     *
     * @param applicationContext the Spring Application context that will be used to resolve the Beans that
     *                           should provide the Data Loaders.
     * @return true if reload was successful, false if an error occurred
     */
    @Override
    public boolean reloadDataLoaders(ApplicationContext applicationContext) {
        logger.info(
                "Programmatic data loader reload requested for application context {}:{}.",
                applicationContext.getId(),
                applicationContext.getApplicationName());
        try {
            long start = System.currentTimeMillis();
            boolean success = reloadableProvider.forceReload(applicationContext);
            if (!success) {
                logger.warn(
                        "Unsuccessful data loader reload reported for application context {}:{}.",
                        applicationContext.getId(),
                        applicationContext.getApplicationName());
                return false;
            }
            long duration = System.currentTimeMillis() - start;

            recordReload(duration);

            logger.info(
                    "Data loader reload completed successfully in {}ms {}:{}",
                    duration,
                    applicationContext.getId(),
                    applicationContext.getApplicationName());
            return true;
        } catch (Exception e) {
            logger.error(
                    "Failed to reload data loader for application context {}:{}.",
                    applicationContext.getId(),
                    applicationContext.getApplicationName(),
                    e);
            return false;
        }
    }

    private void recordReload(long duration) {
        lastReloadTime = Instant.now();
        lastReloadDuration = duration;
        totalReloads.incrementAndGet();
    }

    /**
     * Check if data loader reloading is currently enabled.
     *
     * <p>This implementation always returns true since this controller is only
     * instantiated when reload functionality is enabled.
     *
     * @return true (always, since controller only exists when enabled)
     */
    @Override
    public boolean isReloadEnabled() {
        return true;
    }

    /**
     * Get the timestamp of the last data loader reload.
     *
     * @return Instant of last reload, or null if no reloads have been performed
     */
    @Override
    public Instant getLastReloadTime() {
        return lastReloadTime;
    }

    /**
     * Get comprehensive statistics about data loader reloading.
     *
     * @return DgsDataLoaderReloadStats containing current reload information
     */
    @Override
    public DgsDataLoaderReloadStats getReloadStats() {
        return new DgsDataLoaderReloadStats(totalReloads.get(), lastReloadTime, lastReloadDuration, true);
    }
}
