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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "dgs.graphql.apq")
public class DgsAPQSupportProperties {
    public static final boolean DEFAULT_ENABLED = false;
    public static final boolean DEFAULT_CACHE_CAFFEINE_ENABLED = true;
    public static final String DEFAULT_CACHE_CAFFEINE_SPEC = "maximumSize=100,expireAfterWrite=1h,recordStats";

    public static final String PREFIX = "dgs.graphql.apq";
    public static final String CACHE_PREFIX = PREFIX + ".default-cache";

    /** Enables/Disables support for Automated Persisted Queries (APQ). */
    private boolean enabled = DEFAULT_ENABLED;

    @NestedConfigurationProperty
    private DgsAPQDefaultCaffeineCacheProperties defaultCache = new DgsAPQDefaultCaffeineCacheProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public DgsAPQDefaultCaffeineCacheProperties getDefaultCache() {
        return defaultCache;
    }

    public void setDefaultCache(DgsAPQDefaultCaffeineCacheProperties defaultCache) {
        this.defaultCache = defaultCache;
    }

    public static class DgsAPQDefaultCaffeineCacheProperties {
        /** Enables/Disables the APQ default cache, backed by a Caffeine Cache. */
        private boolean enabled = DEFAULT_CACHE_CAFFEINE_ENABLED;

        /** Defines the Caffeine Spec used by the default cache. */
        private String caffeineSpec = DEFAULT_CACHE_CAFFEINE_SPEC;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCaffeineSpec() {
            return caffeineSpec;
        }

        public void setCaffeineSpec(String caffeineSpec) {
            this.caffeineSpec = caffeineSpec;
        }
    }
}
