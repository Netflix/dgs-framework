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

package com.netflix.graphql.dgs.autoconfig

import com.netflix.graphql.dgs.DgsDataLoaderOptionsProvider
import com.netflix.graphql.dgs.DgsDefaultPreparsedDocumentProvider
import com.netflix.graphql.dgs.DgsFederationResolver
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.conditionals.ConditionalOnJava21
import com.netflix.graphql.dgs.context.DgsCustomContextBuilder
import com.netflix.graphql.dgs.context.DgsCustomContextBuilderWithRequest
import com.netflix.graphql.dgs.context.GraphQLContextContributor
import com.netflix.graphql.dgs.context.GraphQLContextContributorInstrumentation
import com.netflix.graphql.dgs.exceptions.DefaultDataFetcherExceptionHandler
import com.netflix.graphql.dgs.internal.*
import com.netflix.graphql.dgs.internal.DefaultDgsQueryExecutor.ReloadSchemaIndicator
import com.netflix.graphql.dgs.internal.method.ArgumentResolver
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import com.netflix.graphql.dgs.scalars.UploadScalar
import com.netflix.graphql.mocking.MockProvider
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.ExecutionIdProvider
import graphql.execution.ExecutionStrategy
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.preparsed.PreparsedDocumentProvider
import graphql.schema.DataFetcherFactory
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLSchema
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY
import graphql.schema.visibility.GraphqlFieldVisibility
import graphql.schema.visibility.NoIntrospectionGraphqlFieldVisibility.NO_INTROSPECTION_FIELD_VISIBILITY
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.PriorityOrdered
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.WebRequest
import java.time.Duration
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * Framework auto configuration based on open source Spring only, without Netflix integrations.
 * This does NOT have logging, tracing, metrics and security integration.
 */
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@AutoConfiguration(afterName = ["org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration"])
@EnableConfigurationProperties(DgsConfigurationProperties::class, DgsDataloaderConfigurationProperties::class)
@ImportAutoConfiguration(classes = [JacksonAutoConfiguration::class, DgsInputArgumentConfiguration::class])
open class DgsAutoConfiguration(
    private val configProps: DgsConfigurationProperties,
    private val dataloaderConfigProps: DgsDataloaderConfigurationProperties
) {

    companion object {
        const val AUTO_CONF_PREFIX = "dgs.graphql"
        private val LOG: Logger = LoggerFactory.getLogger(DgsAutoConfiguration::class.java)
    }

    @Bean
    @Order(PriorityOrdered.HIGHEST_PRECEDENCE)
    open fun graphQLContextContributionInstrumentation(
        graphQLContextContributors: ObjectProvider<GraphQLContextContributor>
    ): Instrumentation {
        return GraphQLContextContributorInstrumentation(graphQLContextContributors.orderedStream().toList())
    }

    @Bean
    open fun dgsQueryExecutor(
        applicationContext: ApplicationContext,
        schema: GraphQLSchema,
        schemaProvider: DgsSchemaProvider,
        dgsDataLoaderProvider: DgsDataLoaderProvider,
        dgsContextBuilder: DefaultDgsGraphQLContextBuilder,
        dataFetcherExceptionHandler: DataFetcherExceptionHandler,
        instrumentations: ObjectProvider<Instrumentation>,
        environment: Environment,
        @Qualifier("query") providedQueryExecutionStrategy: Optional<ExecutionStrategy>,
        @Qualifier("mutation") providedMutationExecutionStrategy: Optional<ExecutionStrategy>,
        idProvider: Optional<ExecutionIdProvider>,
        reloadSchemaIndicator: ReloadSchemaIndicator,
        preparsedDocumentProvider: ObjectProvider<PreparsedDocumentProvider>,
        queryValueCustomizer: QueryValueCustomizer,
        requestCustomizer: ObjectProvider<DgsQueryExecutorRequestCustomizer>
    ): DgsQueryExecutor {
        val queryExecutionStrategy =
            providedQueryExecutionStrategy.orElse(AsyncExecutionStrategy(dataFetcherExceptionHandler))
        val mutationExecutionStrategy =
            providedMutationExecutionStrategy.orElse(AsyncSerialExecutionStrategy(dataFetcherExceptionHandler))

        val instrumentationImpls = instrumentations.orderedStream().toList()
        val instrumentation: Instrumentation? = when {
            instrumentationImpls.size == 1 -> instrumentationImpls.single()
            instrumentationImpls.isNotEmpty() -> ChainedInstrumentation(instrumentationImpls)
            else -> null
        }

        return DefaultDgsQueryExecutor(
            defaultSchema = schema,
            schemaProvider = schemaProvider,
            dataLoaderProvider = dgsDataLoaderProvider,
            contextBuilder = dgsContextBuilder,
            instrumentation = instrumentation,
            queryExecutionStrategy = queryExecutionStrategy,
            mutationExecutionStrategy = mutationExecutionStrategy,
            idProvider = idProvider,
            reloadIndicator = reloadSchemaIndicator,
            preparsedDocumentProvider = preparsedDocumentProvider.ifAvailable,
            queryValueCustomizer = queryValueCustomizer,
            requestCustomizer = requestCustomizer.getIfAvailable(DgsQueryExecutorRequestCustomizer::DEFAULT_REQUEST_CUSTOMIZER)
        )
    }

    @Bean
    @ConditionalOnMissingBean
    open fun defaultQueryValueCustomizer(): QueryValueCustomizer {
        return QueryValueCustomizer { a -> a }
    }

    @Bean
    @ConditionalOnMissingBean
    open fun dgsDataLoaderOptionsProvider(): DgsDataLoaderOptionsProvider {
        return DefaultDataLoaderOptionsProvider()
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = ["dgsScheduledExecutorService"])
    @Qualifier("dgsScheduledExecutorService")
    open fun dgsScheduledExecutorService(): ScheduledExecutorService {
        return Executors.newSingleThreadScheduledExecutor()
    }

    @Bean
    open fun dgsDataLoaderProvider(applicationContext: ApplicationContext, dataloaderOptionProvider: DgsDataLoaderOptionsProvider, @Qualifier("dgsScheduledExecutorService") dgsScheduledExecutorService: ScheduledExecutorService): DgsDataLoaderProvider {
        return DgsDataLoaderProvider(applicationContext, dataloaderOptionProvider, dgsScheduledExecutorService, dataloaderConfigProps.scheduleDuration, dataloaderConfigProps.tickerModeEnabled)
    }

    /**
     * Used by the [DefaultDgsQueryExecutor], it controls if, and when, such executor should reload the schema.
     * This implementation will return either the boolean value of the `dgs.reload` flag
     * or `true` if the `laptop` profile is an active Spring Boot profiles.
     * <p>
     * You can provide a bean of type [ReloadSchemaIndicator] if you want to control when the
     * [DefaultDgsQueryExecutor] should reload the schema.
     *
     * @implSpec the implementation of such bean should be thread-safe.
     */
    @Bean
    @ConditionalOnMissingBean
    open fun defaultReloadSchemaIndicator(environment: Environment): ReloadSchemaIndicator {
        val isLaptopProfile = environment.activeProfiles.contains("laptop")
        val hotReloadSetting = environment.getProperty("dgs.reload", Boolean::class.java, isLaptopProfile)

        return ReloadSchemaIndicator {
            hotReloadSetting
        }
    }

    @Bean
    @ConditionalOnMissingBean
    open fun dgsSchemaProvider(
        applicationContext: ApplicationContext,
        federationResolver: Optional<DgsFederationResolver>,
        existingTypeDefinitionFactory: Optional<TypeDefinitionRegistry>,
        existingCodeRegistry: Optional<GraphQLCodeRegistry>,
        mockProviders: ObjectProvider<MockProvider>,
        dataFetcherResultProcessors: List<DataFetcherResultProcessor>,
        dataFetcherExceptionHandler: Optional<DataFetcherExceptionHandler> = Optional.empty(),
        entityFetcherRegistry: EntityFetcherRegistry,
        defaultDataFetcherFactory: Optional<DataFetcherFactory<*>> = Optional.empty(),
        methodDataFetcherFactory: MethodDataFetcherFactory
    ): DgsSchemaProvider {
        return DgsSchemaProvider(
            applicationContext = applicationContext,
            federationResolver = federationResolver,
            existingTypeDefinitionRegistry = existingTypeDefinitionFactory,
            mockProviders = mockProviders.toSet(),
            schemaLocations = configProps.schemaLocations,
            dataFetcherResultProcessors = dataFetcherResultProcessors,
            dataFetcherExceptionHandler = dataFetcherExceptionHandler,
            entityFetcherRegistry = entityFetcherRegistry,
            defaultDataFetcherFactory = defaultDataFetcherFactory,
            methodDataFetcherFactory = methodDataFetcherFactory,
            schemaWiringValidationEnabled = configProps.schemaWiringValidationEnabled,
            enableEntityFetcherCustomScalarParsing = configProps.enableEntityFetcherCustomScalarParsing
        )
    }

    @Bean
    open fun entityFetcherRegistry(): EntityFetcherRegistry {
        return EntityFetcherRegistry()
    }

    @Bean
    @ConditionalOnMissingBean
    open fun dataFetcherExceptionHandler(): DataFetcherExceptionHandler {
        return DefaultDataFetcherExceptionHandler()
    }

    @Bean
    @ConditionalOnMissingBean
    open fun schema(dgsSchemaProvider: DgsSchemaProvider, fieldVisibility: GraphqlFieldVisibility): GraphQLSchema {
        return dgsSchemaProvider.schema(null, fieldVisibility)
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "$AUTO_CONF_PREFIX.preparsedDocumentProvider",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false
    )
    @ConditionalOnMissingBean
    open fun preparsedDocumentProvider(configProps: DgsConfigurationProperties): PreparsedDocumentProvider {
        return DgsDefaultPreparsedDocumentProvider(
            configProps.preparsedDocumentProvider.maximumCacheSize,
            Duration.parse(configProps.preparsedDocumentProvider.cacheValidityDuration)
        )
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "$AUTO_CONF_PREFIX.introspection",
        name = ["enabled"],
        havingValue = "false",
        matchIfMissing = false
    )
    open fun noIntrospectionFieldVisibility(): GraphqlFieldVisibility {
        return NO_INTROSPECTION_FIELD_VISIBILITY
    }

    @Bean
    @ConditionalOnMissingBean
    open fun defaultFieldVisibility(): GraphqlFieldVisibility {
        return DEFAULT_FIELD_VISIBILITY
    }

    @Bean
    @ConditionalOnMissingBean
    open fun graphQLContextBuilder(
        dgsCustomContextBuilder: Optional<DgsCustomContextBuilder<*>>,
        dgsCustomContextBuilderWithRequest: Optional<DgsCustomContextBuilderWithRequest<*>>
    ): DefaultDgsGraphQLContextBuilder {
        return DefaultDgsGraphQLContextBuilder(dgsCustomContextBuilder, dgsCustomContextBuilderWithRequest)
    }

    @Bean
    open fun uploadScalar(): UploadScalar {
        return UploadScalar()
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = ["reactor.core.publisher.Mono"])
    open fun monoReactiveDataFetcherResultProcessor(): MonoDataFetcherResultProcessor {
        return MonoDataFetcherResultProcessor()
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = ["kotlinx.coroutines.flow.Flow"])
    open fun flowReactiveDataFetcherResultProcessor(): FlowDataFetcherResultProcessor {
        return FlowDataFetcherResultProcessor()
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = ["reactor.core.publisher.Flux"])
    open fun fluxReactiveDataFetcherResultProcessor(): FluxDataFetcherResultProcessor {
        return FluxDataFetcherResultProcessor()
    }

    /**
     * JDK 21+ only - Creates the dgsAsyncTaskExecutor which is used to run data fetchers automatically wrapped in CompletableFuture.
     * Can be provided by other frameworks to enable context propagation.
     */
    @Bean
    @Qualifier("dgsAsyncTaskExecutor")
    @ConditionalOnJava21
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = ["dgs.graphql.virtualthreads.enabled"], havingValue = "true", matchIfMissing = false)
    open fun virtualThreadsTaskExecutor(): AsyncTaskExecutor {
        LOG.info("Enabling virtual threads for DGS")
        return VirtualThreadTaskExecutor()
    }

    @Bean
    open fun methodDataFetcherFactory(argumentResolvers: ObjectProvider<ArgumentResolver>, @Qualifier("dgsAsyncTaskExecutor") taskExecutorOptional: Optional<AsyncTaskExecutor>): MethodDataFetcherFactory {
        val taskExecutor = if (taskExecutorOptional.isPresent) {
            taskExecutorOptional.get()
        } else {
            null
        }

        return MethodDataFetcherFactory(argumentResolvers.orderedStream().toList(), DefaultParameterNameDiscoverer(), taskExecutor)
    }

    @Bean
    @ConditionalOnClass(name = ["org.springframework.mock.web.MockHttpServletRequest"])
    open fun mockRequestHeaderCustomizer(): DgsQueryExecutorRequestCustomizer {
        /**
         * [DgsQueryExecutorRequestCustomizer] implementation which copies headers into
         * the request if the request is [MockHttpServletRequest]; intended to support
         * test use cases.
         */
        return object : DgsQueryExecutorRequestCustomizer {
            override fun apply(request: WebRequest?, headers: HttpHeaders?): WebRequest? {
                if (headers.isNullOrEmpty() || request !is NativeWebRequest) {
                    return request
                }
                val mockRequest = request.nativeRequest as? MockHttpServletRequest
                    ?: return request
                headers.forEach { key, value ->
                    if (mockRequest.getHeader(key) == null) {
                        mockRequest.addHeader(key, value)
                    }
                }
                return request
            }

            override fun toString(): String {
                return "{MockRequestHeaderCustomizer}"
            }
        }
    }
}
