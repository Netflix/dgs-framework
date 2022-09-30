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

package com.netflix.graphql.dgs.cacheControl

import com.apollographql.federation.graphqljava.caching.CacheControlInstrumentation
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Automatic configuration of [Apollo Server-side caching using @cacheControl](https://www.apollographql.com/docs/apollo-server/performance/caching).
 *
 * This class configures a [CacheControlInstrumentation] when [DgsCacheControlSupportProperties.enabled] is true.
 */
@Suppress("LiftReturnOrAssignment")
@Configuration
@ConditionalOnProperty(
    prefix = DgsCacheControlSupportProperties.PREFIX,
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = DgsCacheControlSupportProperties.DEFAULT_ENABLED
)
@EnableConfigurationProperties(value = [DgsCacheControlSupportProperties::class])
open class DgsCacheControlAutoConfiguration {

    @Bean
    open fun cacheControlInstrumentation(
        cacheControlSupportProperties: DgsCacheControlSupportProperties
    ): CacheControlInstrumentation {
        val defaultMaxAge: Int? = cacheControlSupportProperties.defaultMaxAge
        if (defaultMaxAge == null || defaultMaxAge < 0) {
            return CacheControlInstrumentation()
        } else {
            return CacheControlInstrumentation(defaultMaxAge)
        }
    }
}
