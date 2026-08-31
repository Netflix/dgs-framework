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

package com.netflix.graphql.dgs.metrics.micrometer;

import com.netflix.graphql.dgs.internal.DgsSchemaProvider;
import com.netflix.graphql.dgs.metrics.micrometer.dataloader.DgsDataLoaderInstrumentationProvider;
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsContextualTagCustomizer;
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsExecutionTagCustomizer;
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsFieldFetchTagCustomizer;
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsGraphQLMetricsTagsProvider;
import com.netflix.graphql.dgs.metrics.micrometer.tagging.SimpleGqlOutcomeTagCustomizer;
import com.netflix.graphql.dgs.metrics.micrometer.utils.CacheableQuerySignatureRepository;
import com.netflix.graphql.dgs.metrics.micrometer.utils.QuerySignatureRepository;
import com.netflix.graphql.dgs.metrics.micrometer.utils.SimpleQuerySignatureRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.autoconfigure.metrics.PropertiesAutoTimer;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.Collection;
import java.util.Optional;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} for instrumentation of
 * Spring GraphQL endpoints.
 */
@ConditionalOnClass({MetricsAutoConfiguration.class, MeterRegistry.class})
@AutoConfiguration(after = CompositeMeterRegistryAutoConfiguration.class)
@ConditionalOnProperty(
        prefix = "management.metrics.dgs-graphql",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DgsGraphQLMicrometerAutoConfiguration {
    public static final String AUTO_CONF_PREFIX = "management.metrics.dgs-graphql";
    public static final String AUTO_CONF_QUERY_SIG_PREFIX = AUTO_CONF_PREFIX + ".query-signature";
    public static final String AUTO_CONF_TAG_CUSTOMIZERS = AUTO_CONF_PREFIX + ".tag-customizers";

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    @ConditionalOnProperty(
            prefix = AUTO_CONF_PREFIX + ".instrumentation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public DgsGraphQLMetricsInstrumentation metricsInstrumentation(
            DgsSchemaProvider dgsSchemaProvider,
            DgsMeterRegistrySupplier meterRegistrySupplier,
            DgsGraphQLMetricsTagsProvider tagsProvider,
            DgsGraphQLMetricsProperties properties,
            LimitedTagMetricResolver limitedTagMetricResolver,
            Optional<QuerySignatureRepository> optQuerySignatureRepository) {
        return new DgsGraphQLMetricsInstrumentation(
                dgsSchemaProvider,
                meterRegistrySupplier,
                tagsProvider,
                properties,
                limitedTagMetricResolver,
                optQuerySignatureRepository,
                new PropertiesAutoTimer(properties.getAutotime()));
    }

    @Bean
    @ConditionalOnProperty(
            prefix = AUTO_CONF_PREFIX + ".data-loader-instrumentation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public DgsDataLoaderInstrumentationProvider dataLoaderInstrumentationProvider(
            DgsMeterRegistrySupplier meterRegistrySupplier) {
        return new DgsDataLoaderInstrumentationProvider(meterRegistrySupplier);
    }

    @Bean
    public DgsGraphQLMetricsTagsProvider collatedMetricsTagsProvider(
            Collection<DgsContextualTagCustomizer> contextualTagCustomizer,
            Collection<DgsExecutionTagCustomizer> executionTagCustomizer,
            Collection<DgsFieldFetchTagCustomizer> fieldFetchTagCustomizer) {
        return new DgsGraphQLCollatedMetricsTagsProvider(
                contextualTagCustomizer, executionTagCustomizer, fieldFetchTagCustomizer);
    }

    @Bean
    @ConditionalOnMissingBean
    public LimitedTagMetricResolver spectatorLimitedTagMetricResolve(DgsGraphQLMetricsProperties properties) {
        return new SpectatorLimitedTagMetricResolver(properties.getTags());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DgsGraphQLMetricsProperties.class)
    public static class MetricsPropertiesConfiguration {
    }

    @Configuration
    @ConditionalOnProperty(
            prefix = AUTO_CONF_QUERY_SIG_PREFIX,
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public static class QuerySignatureRepositoryConfiguration {
        public static final String AUTO_CONF_QUERY_SIG_CACHING_PREFIX = AUTO_CONF_QUERY_SIG_PREFIX + ".caching";

        @Bean
        @ConditionalOnMissingBean(QuerySignatureRepository.class)
        @ConditionalOnProperty(
                prefix = AUTO_CONF_QUERY_SIG_CACHING_PREFIX,
                name = "enabled",
                havingValue = "true",
                matchIfMissing = true)
        public QuerySignatureRepository querySignatureCacheableRepository(
                DgsGraphQLMetricsProperties properties,
                DgsMeterRegistrySupplier meterRegistrySupplier,
                Optional<CacheManager> optCacheManager) {
            return new CacheableQuerySignatureRepository(
                    new PropertiesAutoTimer(properties.getAutotime()), meterRegistrySupplier, optCacheManager);
        }

        @Bean
        @ConditionalOnMissingBean(QuerySignatureRepository.class)
        public QuerySignatureRepository simpleQuerySignatureRepository(
                DgsGraphQLMetricsProperties properties, DgsMeterRegistrySupplier meterRegistrySupplier) {
            return new SimpleQuerySignatureRepository(
                    new PropertiesAutoTimer(properties.getAutotime()), meterRegistrySupplier);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class MeterRegistryConfiguration {
        @Bean
        @ConditionalOnMissingBean
        public DgsMeterRegistrySupplier meterRegistrySupplier(ObjectProvider<MeterRegistry> meterRegistryProvider) {
            return new DefaultMeterRegistrySupplier(meterRegistryProvider);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class OptionalTagCustomizersConfiguration {
        @Bean
        @ConditionalOnProperty(
                prefix = AUTO_CONF_TAG_CUSTOMIZERS + ".outcome",
                name = "enabled",
                havingValue = "true",
                matchIfMissing = true)
        public SimpleGqlOutcomeTagCustomizer simpleGqlOutcomeTagCustomizer() {
            return new SimpleGqlOutcomeTagCustomizer();
        }
    }

    static class DefaultMeterRegistrySupplier implements DgsMeterRegistrySupplier {
        /** Fallback Micrometer {@link MeterRegistry} used in case the {@link ObjectProvider} doesn't define one. */
        private static final MeterRegistry DEFAULT_METER_REGISTRY = new SimpleMeterRegistry();

        private final ObjectProvider<MeterRegistry> meterRegistryProvider;

        private volatile MeterRegistry registry;

        DefaultMeterRegistrySupplier(ObjectProvider<MeterRegistry> meterRegistryProvider) {
            this.meterRegistryProvider = meterRegistryProvider;
        }

        @Override
        public MeterRegistry get() {
            MeterRegistry resolved = registry;
            if (resolved == null) {
                resolved = meterRegistryProvider.getIfAvailable(() -> DEFAULT_METER_REGISTRY);
                registry = resolved;
            }
            return resolved;
        }
    }
}
