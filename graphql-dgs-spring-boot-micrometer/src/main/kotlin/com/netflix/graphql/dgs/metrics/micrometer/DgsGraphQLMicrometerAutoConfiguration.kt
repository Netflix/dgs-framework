package com.netflix.graphql.dgs.metrics.micrometer

import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.metrics.micrometer.DgsGraphQLMicrometerAutoConfiguration.Companion.AUTO_CONF_PREFIX
import com.netflix.graphql.dgs.metrics.micrometer.dataloader.DgsDataLoaderInstrumentationProvider
import com.netflix.graphql.dgs.metrics.micrometer.tagging.*
import com.netflix.graphql.dgs.metrics.micrometer.utils.CacheableQuerySignatureRepository
import com.netflix.graphql.dgs.metrics.micrometer.utils.QuerySignatureRepository
import com.netflix.graphql.dgs.metrics.micrometer.utils.SimpleQuerySignatureRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

/**
 * [Auto-configuration][org.springframework.boot.autoconfigure.EnableAutoConfiguration] for instrumentation of Spring GraphQL
 * endpoints.
 */
@AutoConfigureAfter(CompositeMeterRegistryAutoConfiguration::class)
@ConditionalOnClass(MetricsAutoConfiguration::class, MeterRegistry::class)
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = AUTO_CONF_PREFIX, name = ["enabled"], havingValue = "true", matchIfMissing = true)
open class DgsGraphQLMicrometerAutoConfiguration {

    companion object {
        const val AUTO_CONF_PREFIX = "management.metrics.dgs-graphql"
        const val AUTO_CONF_QUERY_SIG_PREFIX = "$AUTO_CONF_PREFIX.query-signature"
        const val AUTO_CONF_TAG_CUSTOMIZERS = "$AUTO_CONF_PREFIX.tag-customizers"
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "$AUTO_CONF_PREFIX.instrumentation",
        name = ["enabled"], havingValue = "true", matchIfMissing = true
    )
    open fun metricsInstrumentation(
        dgsSchemaProvider: DgsSchemaProvider,
        meterRegistrySupplier: DgsMeterRegistrySupplier,
        tagsProvider: DgsGraphQLMetricsTagsProvider,
        properties: DgsGraphQLMetricsProperties,
        limitedTagMetricResolver: LimitedTagMetricResolver,
        optQuerySignatureRepository: Optional<QuerySignatureRepository>
    ): DgsGraphQLMetricsInstrumentation {
        return DgsGraphQLMetricsInstrumentation(
            dgsSchemaProvider,
            meterRegistrySupplier,
            tagsProvider,
            properties,
            limitedTagMetricResolver,
            optQuerySignatureRepository
        )
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "$AUTO_CONF_PREFIX.data-loader-instrumentation",
        name = ["enabled"], havingValue = "true", matchIfMissing = true
    )
    open fun dataLoaderInstrumentationProvider(
        meterRegistrySupplier: DgsMeterRegistrySupplier
    ): DgsDataLoaderInstrumentationProvider {
        return DgsDataLoaderInstrumentationProvider(meterRegistrySupplier)
    }

    @Bean
    open fun collatedMetricsTagsProvider(
        contextualTagCustomizer: Collection<DgsContextualTagCustomizer>,
        executionTagCustomizer: Collection<DgsExecutionTagCustomizer>,
        fieldFetchTagCustomizer: Collection<DgsFieldFetchTagCustomizer>
    ): DgsGraphQLMetricsTagsProvider {
        return DgsGraphQLCollatedMetricsTagsProvider(
            contextualTagCustomizer,
            executionTagCustomizer,
            fieldFetchTagCustomizer
        )
    }

    @Bean
    @ConditionalOnMissingBean
    open fun spectatorLimitedTagMetricResolve(properties: DgsGraphQLMetricsProperties): LimitedTagMetricResolver {
        return SpectatorLimitedTagMetricResolver(properties.tags)
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DgsGraphQLMetricsProperties::class)
    open class MetricsPropertiesConfiguration

    @Configuration
    @ConditionalOnProperty(
        prefix = AUTO_CONF_QUERY_SIG_PREFIX,
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    open class QuerySignatureRepositoryConfiguration {

        companion object {
            const val AUTO_CONF_QUERY_SIG_CACHING_PREFIX = "$AUTO_CONF_QUERY_SIG_PREFIX.caching"
        }

        @Bean
        @ConditionalOnMissingBean(value = [QuerySignatureRepository::class])
        @ConditionalOnProperty(
            prefix = AUTO_CONF_QUERY_SIG_CACHING_PREFIX,
            name = ["enabled"],
            havingValue = "true",
            matchIfMissing = true
        )
        open fun querySignatureCacheableRepository(
            properties: DgsGraphQLMetricsProperties,
            meterRegistrySupplier: DgsMeterRegistrySupplier,
            optCacheManager: Optional<CacheManager>
        ): QuerySignatureRepository {
            return CacheableQuerySignatureRepository(
                properties,
                meterRegistrySupplier,
                optCacheManager
            )
        }

        @Bean
        @ConditionalOnMissingBean(value = [QuerySignatureRepository::class])
        open fun simpleQuerySignatureRepository(
            properties: DgsGraphQLMetricsProperties,
            meterRegistrySupplier: DgsMeterRegistrySupplier
        ): QuerySignatureRepository {
            return SimpleQuerySignatureRepository(properties, meterRegistrySupplier)
        }
    }

    @Configuration(proxyBeanMethods = false)
    open class MeterRegistryConfiguration {
        @Bean
        @ConditionalOnMissingBean
        open fun meterRegistrySupplier(meterRegistryProvider: ObjectProvider<MeterRegistry>): DgsMeterRegistrySupplier {
            return DefaultMeterRegistrySupplier(meterRegistryProvider)
        }
    }

    @Configuration(proxyBeanMethods = false)
    open class OptionalTagCustomizersConfiguration {
        @Bean
        @ConditionalOnProperty(
            prefix = "$AUTO_CONF_TAG_CUSTOMIZERS.outcome",
            name = ["enabled"],
            havingValue = "true",
            matchIfMissing = true
        )
        open fun simpleGqlOutcomeTagCustomizer(): SimpleGqlOutcomeTagCustomizer {
            return SimpleGqlOutcomeTagCustomizer()
        }
    }

    internal class DefaultMeterRegistrySupplier(
        private val meterRegistryProvider: ObjectProvider<MeterRegistry>
    ) : DgsMeterRegistrySupplier {

        companion object {
            /** Fallback Micrometer [MeterRegistry] used in case the [ObjectProvider] doesn't define one. */
            private val DEFAULT_METER_REGISTRY = SimpleMeterRegistry()
        }

        private val registry: MeterRegistry by lazy {
            meterRegistryProvider.ifAvailable ?: DEFAULT_METER_REGISTRY
        }

        override fun get(): MeterRegistry {
            return registry
        }
    }
}
