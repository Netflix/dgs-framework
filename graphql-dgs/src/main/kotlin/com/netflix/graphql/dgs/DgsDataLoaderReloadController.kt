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

package com.netflix.graphql.dgs

import org.springframework.context.ApplicationContext
import java.time.Instant

/**
 * Public API for controlling data loader reloading.
 *
 * This interface provides programmatic access to data loader reload functionality,
 * allowing applications and development tools to trigger reloads as needed.
 *
 * Example usage:
 *
 * ```kotlin
 * @Autowired
 * private lateinit var reloadController: DgsDataLoaderReloadController
 *
 * fun refreshDataLoaders() {
 *     if (reloadController.isReloadEnabled()) {
 *         val success = reloadController.reloadDataLoaders()
 *         if (success) {
 *             log.info("Data loaders reloaded at ${reloadController.getLastReloadTime()}")
 *         }
 *     }
 * }
 * ```
 */
interface DgsDataLoaderReloadController {
    /**
     * Force immediate reload of all data loaders.
     *
     * This will trigger a complete rediscovery of @DgsDataLoader annotations
     * and rebuild the internal data loader registry. The reload is performed
     * synchronously and will affect all subsequent GraphQL requests.
     *
     * @return true if reload was successful, false if an error occurred
     */
    fun reloadDataLoaders(): Boolean

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
    fun reloadDataLoaders(applicationContext: ApplicationContext): Boolean

    /**
     * Check if data loader reloading is currently enabled.
     *
     * This method returns true if the reload functionality is active and
     * available for use. If false, calls to reloadDataLoaders() will
     * have no effect.
     *
     * @return true if reload functionality is active, false otherwise
     */
    fun isReloadEnabled(): Boolean

    /**
     * Get the timestamp of the last data loader reload.
     *
     * This can be used to track when data loaders were last refreshed,
     * which is useful for monitoring and debugging purposes.
     *
     * @return Instant of last reload, or null if never reloaded
     */
    fun getLastReloadTime(): Instant?

    /**
     * Get statistics about data loader reloading.
     *
     * @return DgsDataLoaderReloadStats containing reload information
     */
    fun getReloadStats(): DgsDataLoaderReloadStats

    /**
     * Statistics about data loader reloading operations.
     *
     * @property totalReloads Total number of successful reloads performed
     * @property lastReloadTime Timestamp of the last reload operation
     * @property lastReloadDuration Duration of the last reload operation in milliseconds
     * @property isEnabled Whether reload functionality is currently enabled
     */
    data class DgsDataLoaderReloadStats(
        val totalReloads: Long,
        val lastReloadTime: Instant?,
        val lastReloadDuration: Long?,
        val isEnabled: Boolean,
    )
}
