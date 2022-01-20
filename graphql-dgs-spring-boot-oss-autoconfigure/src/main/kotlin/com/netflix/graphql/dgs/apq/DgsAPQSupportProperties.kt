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

package com.netflix.graphql.dgs.apq

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties(prefix = DgsAPQSupportProperties.PREFIX)
@ConstructorBinding
@Suppress("ConfigurationProperties")
data class DgsAPQSupportProperties(
    /** Enables/Disables support for Automated Persisted Queries (APQ). */
    @DefaultValue("$DEFAULT_ENABLED")
    var enabled: Boolean = DEFAULT_ENABLED,
    @NestedConfigurationProperty
    var defaultCache: DgsAPQDefaultCaffeineCacheProperties = DgsAPQDefaultCaffeineCacheProperties()
) {
    data class DgsAPQDefaultCaffeineCacheProperties(
        /** Enables/Disables the APQ default cache, backed by a Caffeine Cache.*/
        @DefaultValue("$DEFAULT_CACHE_CAFFEINE_ENABLED")
        var enabled: Boolean = DEFAULT_CACHE_CAFFEINE_ENABLED,
        /** Defines the Caffeine Spec used by the default cache.*/
        @DefaultValue(DEFAULT_CACHE_CAFFEINE_SPEC)
        var caffeineSpec: String = DEFAULT_CACHE_CAFFEINE_SPEC
    )

    companion object {
        const val DEFAULT_ENABLED = false
        const val DEFAULT_CACHE_CAFFEINE_ENABLED = true
        const val DEFAULT_CACHE_CAFFEINE_SPEC = "maximumSize=100,expireAfterWrite=1h,recordStats"

        const val PREFIX: String = "dgs.graphql.apq"
        const val CACHE_PREFIX: String = "$PREFIX.default-cache"
    }
}
