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

package com.netflix.graphql.dgs;

import org.springframework.context.ApplicationContext;

import java.time.Instant;
import java.util.Objects;

/**
 * Public API for controlling data loader reloading.
 *
 * <p>This interface provides programmatic access to data loader reload functionality,
 * allowing applications and development tools to trigger reloads as needed.
 */
public interface DgsDataLoaderReloadController {
    /**
     * Force immediate reload of all data loaders.
     *
     * <p>This will trigger a complete rediscovery of @DgsDataLoader annotations
     * and rebuild the internal data loader registry. The reload is performed
     * synchronously and will affect all subsequent GraphQL requests.
     *
     * @return true if reload was successful, false if an error occurred
     */
    boolean reloadDataLoaders();

    /**
     * Force immediate reload of all data loaders defined by an explicit application context.
     *
     * <p>This method delegates to the ReloadableDgsDataLoaderProvider to perform
     * the actual reload operation. It tracks timing and success statistics.
     *
     * @param applicationContext the Spring Application context that will be used to resolve the Beans that
     *                           should provide the Data Loaders.
     * @return true if reload was successful, false if an error occurred
     */
    boolean reloadDataLoaders(ApplicationContext applicationContext);

    /**
     * Check if data loader reloading is currently enabled.
     *
     * @return true if reload functionality is active, false otherwise
     */
    boolean isReloadEnabled();

    /**
     * Get the timestamp of the last data loader reload.
     *
     * @return Instant of last reload, or null if never reloaded
     */
    Instant getLastReloadTime();

    /**
     * Get statistics about data loader reloading.
     *
     * @return DgsDataLoaderReloadStats containing reload information
     */
    DgsDataLoaderReloadStats getReloadStats();

    /** Statistics about data loader reloading operations. */
    final class DgsDataLoaderReloadStats {
        private final long totalReloads;
        private final Instant lastReloadTime;
        private final Long lastReloadDuration;
        private final boolean isEnabled;

        public DgsDataLoaderReloadStats(
                long totalReloads, Instant lastReloadTime, Long lastReloadDuration, boolean isEnabled) {
            this.totalReloads = totalReloads;
            this.lastReloadTime = lastReloadTime;
            this.lastReloadDuration = lastReloadDuration;
            this.isEnabled = isEnabled;
        }

        /** Total number of successful reloads performed. */
        public long getTotalReloads() {
            return totalReloads;
        }

        /** Timestamp of the last reload operation. */
        public Instant getLastReloadTime() {
            return lastReloadTime;
        }

        /** Duration of the last reload operation in milliseconds. */
        public Long getLastReloadDuration() {
            return lastReloadDuration;
        }

        /** Whether reload functionality is currently enabled. */
        public boolean isEnabled() {
            return isEnabled;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof DgsDataLoaderReloadStats that
                    && totalReloads == that.totalReloads
                    && isEnabled == that.isEnabled
                    && Objects.equals(lastReloadTime, that.lastReloadTime)
                    && Objects.equals(lastReloadDuration, that.lastReloadDuration);
        }

        @Override
        public int hashCode() {
            return Objects.hash(totalReloads, lastReloadTime, lastReloadDuration, isEnabled);
        }

        @Override
        public String toString() {
            return "DgsDataLoaderReloadStats(totalReloads=" + totalReloads + ", lastReloadTime=" + lastReloadTime
                    + ", lastReloadDuration=" + lastReloadDuration + ", isEnabled=" + isEnabled + ")";
        }
    }
}
