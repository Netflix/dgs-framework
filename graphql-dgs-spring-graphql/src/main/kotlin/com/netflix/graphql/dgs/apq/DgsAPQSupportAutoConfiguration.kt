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

package com.netflix.graphql.dgs.apq

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.CaffeineSpec
import com.netflix.graphql.dgs.springgraphql.autoconfig.DgsSpringGraphQLAutoConfiguration
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.execution.preparsed.PreparsedDocumentProvider
import graphql.execution.preparsed.persisted.PersistedQueryCache
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.util.Optional

@AutoConfiguration
@AutoConfigureAfter(
    DgsSpringGraphQLAutoConfiguration::class,
    name = ["org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration"],
)
@ConditionalOnProperty(
    prefix = "dgs.graphql.apq",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
@EnableConfigurationProperties(DgsAPQSupportProperties::class)
open class DgsAPQSupportAutoConfiguration {
    @Bean
    open fun apqSourceBuilderCustomizer(
        preparsedDocumentProvider: Optional<PreparsedDocumentProvider>,
        persistedQueryCache: Optional<PersistedQueryCache>,
    ): GraphQlSourceBuilderCustomizer =
        GraphQlSourceBuilderCustomizer { builder ->
            builder.configureGraphQl { graphQlBuilder ->
                // For non-APQ queries, the user specified PreparsedDocumentProvider should be used, so we configure the DgsAPQPreparsedDocumentProvider to
                // wrap the user specified one and delegate appropriately since we can only have one PreParsedDocumentProvider bean
                val apqPreParsedDocumentProvider =
                    DgsAPQPreParsedDocumentProviderWrapper(persistedQueryCache.get(), preparsedDocumentProvider)
                graphQlBuilder.preparsedDocumentProvider(apqPreParsedDocumentProvider)
            }
        }

    @Configuration
    @ConditionalOnClass(name = ["com.github.benmanes.caffeine.cache.Cache"])
    @ConditionalOnProperty(
        prefix = DgsAPQSupportProperties.CACHE_PREFIX,
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = DgsAPQSupportProperties.DEFAULT_CACHE_CAFFEINE_ENABLED,
    )
    open class APQCaffeineCacheConfiguration {
        @Bean(name = [BEAN_APQ_CAFFEINE_CACHE_NAME])
        @ConditionalOnMissingBean(name = [BEAN_APQ_CAFFEINE_CACHE_NAME])
        open fun apqCaffeineCache(properties: DgsAPQSupportProperties): Cache<String, PreparsedDocumentEntry> =
            if (properties.defaultCache.caffeineSpec.isNotBlank()) {
                Caffeine.from(CaffeineSpec.parse(properties.defaultCache.caffeineSpec)).build()
            } else {
                Caffeine
                    .newBuilder()
                    .maximumSize(1000)
                    .expireAfterAccess(Duration.ofHours(1))
                    .build()
            }
    }

    @Configuration
    @ConditionalOnClass(
        name = ["com.github.benmanes.caffeine.cache.Cache"],
    )
    @ConditionalOnBean(io.micrometer.core.instrument.MeterRegistry::class)
    open class APQMicrometerMeteredCaffeineCacheConfiguration {
        @Bean
        @ConditionalOnMissingBean(PersistedQueryCache::class)
        open fun meteredPersistedQueryCache(
            @Qualifier(BEAN_APQ_CAFFEINE_CACHE_NAME) appCaffeine: Cache<String, PreparsedDocumentEntry>,
            meterRegistry: MeterRegistry,
        ): PersistedQueryCache {
            val monitoredCache: Cache<String, PreparsedDocumentEntry> =
                CaffeineCacheMetrics.monitor(meterRegistry, appCaffeine, BEAN_APQ_CAFFEINE_CACHE_NAME)
            return AutomatedPersistedQueryCaffeineCache(monitoredCache)
        }
    }

    // We want this version only if there is no micrometer meter registry
    @Configuration
    @ConditionalOnMissingBean(APQMicrometerMeteredCaffeineCacheConfiguration::class)
    @ConditionalOnClass(name = ["com.github.benmanes.caffeine.cache.Cache"])
    open class APQBasicCaffeineCacheConfiguration {
        @Bean
        @ConditionalOnMissingBean(PersistedQueryCache::class)
        open fun meteredPersistedQueryCache(
            @Qualifier(BEAN_APQ_CAFFEINE_CACHE_NAME) cache: Cache<String, PreparsedDocumentEntry>,
        ): PersistedQueryCache = AutomatedPersistedQueryCaffeineCache(cache)
    }

    companion object {
        const val BEAN_APQ_CAFFEINE_CACHE_NAME = "apqCaffeineCache"
    }
}
