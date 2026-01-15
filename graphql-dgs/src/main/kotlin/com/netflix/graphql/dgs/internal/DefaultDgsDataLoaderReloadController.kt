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

import com.netflix.graphql.dgs.DgsDataLoaderReloadController
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * Default implementation of DgsDataLoaderReloadController.
 *
 * This controller provides programmatic access to data loader reloading functionality
 * when reload mode is enabled. It maintains statistics about reload operations and
 * provides a convenient API for development tools and administrative interfaces.
 *
 * The controller is only registered as a Spring bean when reload functionality is enabled
 * via the 'dgs.reload.enabled' property.
 */
class DefaultDgsDataLoaderReloadController(
    private val reloadableProvider: ReloadableDgsDataLoaderProvider,
) : DgsDataLoaderReloadController {
    @Volatile
    private var lastReloadTime: Instant? = null

    @Volatile
    private var lastReloadDuration: Long? = null

    private val totalReloads = AtomicLong(0)

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DefaultDgsDataLoaderReloadController::class.java)
    }

    /**
     * Force immediate reload of all data loaders.
     *
     * This method delegates to the ReloadableDgsDataLoaderProvider to perform
     * the actual reload operation. It tracks timing and success statistics.
     *
     * @return true if reload was successful, false if an error occurred
     */
    override fun reloadDataLoaders(): Boolean {
        logger.info("Programmatic data loader reload requested")

        return try {
            val duration =
                measureTimeMillis {
                    val success = reloadableProvider.forceReload()
                    if (!success) {
                        logger.warn("Data loader reload reported failure")
                        return false
                    }
                }

            lastReloadTime = Instant.now()
            lastReloadDuration = duration
            totalReloads.incrementAndGet()

            logger.info("Data loader reload completed successfully in {}ms", duration)
            true
        } catch (e: Exception) {
            logger.error("Failed to reload data loaders", e)
            false
        }
    }

    /**
     * Force immediate reload of all data loaders defined by an explicit application context.
     *
     * This method delegates to the ReloadableDgsDataLoaderProvider to perform
     * the actual reload operation. It tracks timing and success statistics.
     *
     * @param applicationContext the Spring Application context that will be used to resolve the Beans that
     *                           should provide the Data  Loaders.
     * @return true if reload was successful, false if an error occurred
     */
    override fun reloadDataLoaders(applicationContext: ApplicationContext): Boolean {
        logger.info(
            "Programmatic data loader reload requested for application context {}:{}.",
            applicationContext.id,
            applicationContext.applicationName,
        )
        return try {
            val duration =
                measureTimeMillis {
                    val success = reloadableProvider.forceReload(applicationContext)
                    if (!success) {
                        logger.warn(
                            "Unsuccessful data loader reload reported for application context {}:{}.",
                            applicationContext.id,
                            applicationContext.applicationName,
                        )
                        return false
                    }
                }

            lastReloadTime = Instant.now()
            lastReloadDuration = duration
            totalReloads.incrementAndGet()

            logger.info(
                "Data loader reload completed successfully in {}ms {}:{}",
                duration,
                applicationContext.id,
                applicationContext.applicationName,
            )
            true
        } catch (e: Exception) {
            logger.error(
                "Failed to reload data loader for application context {}:{}.",
                applicationContext.id,
                applicationContext.applicationName,
                e,
            )
            false
        }
    }

    /**
     * Check if data loader reloading is currently enabled.
     *
     * This implementation always returns true since this controller is only
     * instantiated when reload functionality is enabled.
     *
     * @return true (always, since controller only exists when enabled)
     */
    override fun isReloadEnabled(): Boolean = true

    /**
     * Get the timestamp of the last data loader reload.
     *
     * This returns the timestamp from the most recent successful reload operation
     * performed by this controller. It may not reflect reloads triggered by
     * the ReloadDataLoadersIndicator.
     *
     * @return Instant of last reload, or null if no reloads have been performed
     */
    override fun getLastReloadTime(): Instant? = lastReloadTime

    /**
     * Get comprehensive statistics about data loader reloading.
     *
     * @return DgsDataLoaderReloadStats containing current reload information
     */
    override fun getReloadStats(): DgsDataLoaderReloadController.DgsDataLoaderReloadStats =
        DgsDataLoaderReloadController.DgsDataLoaderReloadStats(
            totalReloads = totalReloads.get(),
            lastReloadTime = lastReloadTime,
            lastReloadDuration = lastReloadDuration,
            isEnabled = true,
        )
}
