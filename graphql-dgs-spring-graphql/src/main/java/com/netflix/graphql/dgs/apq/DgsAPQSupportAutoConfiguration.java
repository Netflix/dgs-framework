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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.netflix.graphql.dgs.springgraphql.autoconfig.DgsSpringGraphQLAutoConfiguration;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.execution.preparsed.persisted.PersistedQueryCache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Optional;

@AutoConfiguration
@AutoConfigureAfter(
        value = DgsSpringGraphQLAutoConfiguration.class,
        name = "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration")
@ConditionalOnProperty(
        prefix = "dgs.graphql.apq",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
@EnableConfigurationProperties(DgsAPQSupportProperties.class)
public class DgsAPQSupportAutoConfiguration {
    public static final String BEAN_APQ_CAFFEINE_CACHE_NAME = "apqCaffeineCache";

    @Bean
    public GraphQlSourceBuilderCustomizer apqSourceBuilderCustomizer(
            Optional<PreparsedDocumentProvider> preparsedDocumentProvider,
            Optional<PersistedQueryCache> persistedQueryCache) {
        return builder -> builder.configureGraphQl(graphQlBuilder -> {
            // For non-APQ queries, the user specified PreparsedDocumentProvider should be used, so we configure the
            // DgsAPQPreparsedDocumentProvider to wrap the user specified one and delegate appropriately since we can
            // only have one PreParsedDocumentProvider bean
            DgsAPQPreParsedDocumentProviderWrapper apqPreParsedDocumentProvider =
                    new DgsAPQPreParsedDocumentProviderWrapper(persistedQueryCache.get(), preparsedDocumentProvider);
            graphQlBuilder.preparsedDocumentProvider(apqPreParsedDocumentProvider);
        });
    }

    @Configuration
    @ConditionalOnClass(name = "com.github.benmanes.caffeine.cache.Cache")
    @ConditionalOnProperty(
            prefix = DgsAPQSupportProperties.CACHE_PREFIX,
            name = "enabled",
            havingValue = "true",
            matchIfMissing = DgsAPQSupportProperties.DEFAULT_CACHE_CAFFEINE_ENABLED)
    public static class APQCaffeineCacheConfiguration {
        @Bean(name = BEAN_APQ_CAFFEINE_CACHE_NAME)
        @ConditionalOnMissingBean(name = BEAN_APQ_CAFFEINE_CACHE_NAME)
        public Cache<String, PreparsedDocumentEntry> apqCaffeineCache(DgsAPQSupportProperties properties) {
            if (!properties.getDefaultCache().getCaffeineSpec().isBlank()) {
                return Caffeine.from(CaffeineSpec.parse(
                                properties.getDefaultCache().getCaffeineSpec()))
                        .build();
            }
            return Caffeine
                    .newBuilder()
                    .maximumSize(1000)
                    .expireAfterAccess(Duration.ofHours(1))
                    .build();
        }
    }

    @Configuration
    @ConditionalOnClass(name = "com.github.benmanes.caffeine.cache.Cache")
    @ConditionalOnBean(MeterRegistry.class)
    public static class APQMicrometerMeteredCaffeineCacheConfiguration {
        @Bean
        @ConditionalOnMissingBean(PersistedQueryCache.class)
        public PersistedQueryCache meteredPersistedQueryCache(
                @Qualifier(BEAN_APQ_CAFFEINE_CACHE_NAME) Cache<String, PreparsedDocumentEntry> appCaffeine,
                MeterRegistry meterRegistry) {
            Cache<String, PreparsedDocumentEntry> monitoredCache =
                    CaffeineCacheMetrics.monitor(meterRegistry, appCaffeine, BEAN_APQ_CAFFEINE_CACHE_NAME);
            return new AutomatedPersistedQueryCaffeineCache(monitoredCache);
        }
    }

    // We want this version only if there is no micrometer meter registry
    @Configuration
    @ConditionalOnMissingBean(
            value = APQMicrometerMeteredCaffeineCacheConfiguration.class,
            name = "io.micrometer.core.instrument.MeterRegistry::class")
    @ConditionalOnMissingClass("io.micrometer.core.instrument.MeterRegistry::class")
    @ConditionalOnClass(name = "com.github.benmanes.caffeine.cache.Cache")
    public static class APQBasicCaffeineCacheConfiguration {
        @Bean
        @ConditionalOnMissingBean(PersistedQueryCache.class)
        public PersistedQueryCache meteredPersistedQueryCache(
                @Qualifier(BEAN_APQ_CAFFEINE_CACHE_NAME) Cache<String, PreparsedDocumentEntry> cache) {
            return new AutomatedPersistedQueryCaffeineCache(cache);
        }
    }
}
