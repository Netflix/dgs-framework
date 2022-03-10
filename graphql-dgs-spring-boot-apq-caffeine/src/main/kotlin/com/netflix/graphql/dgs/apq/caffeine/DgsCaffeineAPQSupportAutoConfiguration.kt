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

package com.netflix.graphql.dgs.apq.caffeine

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.CaffeineSpec
import com.netflix.graphql.dgs.Internal
import com.netflix.graphql.dgs.apq.DgsAPQSupportAutoConfiguration
import com.netflix.graphql.dgs.apq.DgsAPQSupportProperties
import com.netflix.graphql.dgs.apq.caffeine.internal.EnableCaffeineCacheCondition
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.execution.preparsed.persisted.PersistedQueryCache
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@Conditional(EnableCaffeineCacheCondition::class)
@EnableConfigurationProperties(DgsCaffeineAPQSupportProperties::class)
@AutoConfigureBefore(DgsAPQSupportAutoConfiguration::class)
@Internal
open class DgsCaffeineAPQSupportAutoConfiguration {

    @Configuration
    @ConditionalOnClass(name = ["com.github.benmanes.caffeine.cache.Cache"])
    @ConditionalOnProperty(
        prefix = DgsAPQSupportProperties.PREFIX,
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = DgsAPQSupportProperties.DEFAULT_ENABLE_APQ
    )
    open class APQCaffeineCacheConfiguration {

        @Bean(name = [CaffeineApqCache.CAFFEINE_APQ_CACHE_BEAN_NAME])
        @ConditionalOnMissingBean(name = [CaffeineApqCache.CAFFEINE_APQ_CACHE_BEAN_NAME])
        @Suppress("UNCHECKED_CAST")
        open fun apqCaffeineCache(properties: DgsCaffeineAPQSupportProperties): Cache<String, PreparsedDocumentEntry> {
            return if (properties.caffeineSpec.isNotBlank()) {
                Caffeine.from(CaffeineSpec.parse(properties.caffeineSpec)).build()
            } else {
                Caffeine.newBuilder()
                    .maximumSize(1000)
                    .expireAfterAccess(Duration.ofHours(1))
                    .build()
            }
        }
    }

    @Configuration
    @ConditionalOnClass(
        name = ["io.micrometer.core.instrument.MeterRegistry", "com.github.benmanes.caffeine.cache.Cache"]
    )
    open class APQMicrometerMeteredCaffeineCacheConfiguration {

        @Bean
        @ConditionalOnBean(MeterRegistry::class)
        @ConditionalOnMissingBean(PersistedQueryCache::class)
        open fun meteredPersistedQueryCache(
            @Qualifier(CaffeineApqCache.CAFFEINE_APQ_CACHE_BEAN_NAME) appCaffeine: Cache<String, PreparsedDocumentEntry>,
            meterRegistry: MeterRegistry
        ): PersistedQueryCache {
            val monitoredCache: Cache<String, PreparsedDocumentEntry> =
                CaffeineCacheMetrics.monitor(meterRegistry, appCaffeine, CaffeineApqCache.CAFFEINE_APQ_CACHE_BEAN_NAME)
            return AutomatedPersistedQueryCaffeineCache(monitoredCache)
        }
    }

    @Configuration
    @ConditionalOnMissingBean(APQMicrometerMeteredCaffeineCacheConfiguration::class)
    @ConditionalOnClass(name = ["com.github.benmanes.caffeine.cache.Cache"])
    open class APQBasicCaffeineCacheConfiguration {

        @Bean
        @ConditionalOnMissingBean(PersistedQueryCache::class)
        open fun meteredPersistedQueryCache(
            @Qualifier(CaffeineApqCache.CAFFEINE_APQ_CACHE_BEAN_NAME) cache: Cache<String, PreparsedDocumentEntry>
        ): PersistedQueryCache {
            return AutomatedPersistedQueryCaffeineCache(cache)
        }
    }
}
