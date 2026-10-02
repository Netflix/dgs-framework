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

package com.netflix.graphql.dgs.apq;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.Objects;

@ConfigurationProperties(prefix = "dgs.graphql.apq")
public class DgsAPQSupportProperties {
    public static final boolean DEFAULT_ENABLED = false;
    public static final boolean DEFAULT_CACHE_CAFFEINE_ENABLED = true;
    @NotNull
    public static final String DEFAULT_CACHE_CAFFEINE_SPEC = "maximumSize=100,expireAfterWrite=1h,recordStats";

    @NotNull
    public static final String PREFIX = "dgs.graphql.apq";
    @NotNull
    public static final String CACHE_PREFIX = PREFIX + ".default-cache";

    /** Enables/Disables support for Automated Persisted Queries (APQ). */
    private boolean enabled = DEFAULT_ENABLED;

    @NestedConfigurationProperty
    private DgsAPQDefaultCaffeineCacheProperties defaultCache = new DgsAPQDefaultCaffeineCacheProperties();

    public DgsAPQSupportProperties() {
    }

    public DgsAPQSupportProperties(boolean enabled, @NotNull DgsAPQDefaultCaffeineCacheProperties defaultCache) {
        this.enabled = enabled;
        this.defaultCache = defaultCache;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Retained for compatibility with the former Kotlin property getter. */
    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @NotNull
    public DgsAPQDefaultCaffeineCacheProperties getDefaultCache() {
        return defaultCache;
    }

    public void setDefaultCache(@NotNull DgsAPQDefaultCaffeineCacheProperties defaultCache) {
        this.defaultCache = defaultCache;
    }

    public boolean component1() {
        return enabled;
    }

    @NotNull
    public DgsAPQDefaultCaffeineCacheProperties component2() {
        return defaultCache;
    }

    @NotNull
    public DgsAPQSupportProperties copy(boolean enabled, @NotNull DgsAPQDefaultCaffeineCacheProperties defaultCache) {
        return new DgsAPQSupportProperties(enabled, defaultCache);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof DgsAPQSupportProperties that
                && enabled == that.enabled
                && Objects.equals(defaultCache, that.defaultCache);
    }

    @Override
    public int hashCode() {
        return 31 * Boolean.hashCode(enabled) + Objects.hashCode(defaultCache);
    }

    @NotNull
    @Override
    public String toString() {
        return "DgsAPQSupportProperties(enabled=" + enabled + ", defaultCache=" + defaultCache + ")";
    }

    public static class DgsAPQDefaultCaffeineCacheProperties {
        /** Enables/Disables the APQ default cache, backed by a Caffeine Cache. */
        private boolean enabled = DEFAULT_CACHE_CAFFEINE_ENABLED;

        /** Defines the Caffeine Spec used by the default cache. */
        private String caffeineSpec = DEFAULT_CACHE_CAFFEINE_SPEC;

        public DgsAPQDefaultCaffeineCacheProperties() {
        }

        public DgsAPQDefaultCaffeineCacheProperties(boolean enabled, @NotNull String caffeineSpec) {
            this.enabled = enabled;
            this.caffeineSpec = caffeineSpec;
        }

        public boolean isEnabled() {
            return enabled;
        }

        /** Retained for compatibility with the former Kotlin property getter. */
        public boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @NotNull
        public String getCaffeineSpec() {
            return caffeineSpec;
        }

        public void setCaffeineSpec(@NotNull String caffeineSpec) {
            this.caffeineSpec = caffeineSpec;
        }

        public boolean component1() {
            return enabled;
        }

        @NotNull
        public String component2() {
            return caffeineSpec;
        }

        @NotNull
        public DgsAPQDefaultCaffeineCacheProperties copy(boolean enabled, @NotNull String caffeineSpec) {
            return new DgsAPQDefaultCaffeineCacheProperties(enabled, caffeineSpec);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof DgsAPQDefaultCaffeineCacheProperties that
                    && enabled == that.enabled
                    && Objects.equals(caffeineSpec, that.caffeineSpec);
        }

        @Override
        public int hashCode() {
            return 31 * Boolean.hashCode(enabled) + Objects.hashCode(caffeineSpec);
        }

        @NotNull
        @Override
        public String toString() {
            return "DgsAPQDefaultCaffeineCacheProperties(enabled=" + enabled + ", caffeineSpec=" + caffeineSpec + ")";
        }
    }
}
