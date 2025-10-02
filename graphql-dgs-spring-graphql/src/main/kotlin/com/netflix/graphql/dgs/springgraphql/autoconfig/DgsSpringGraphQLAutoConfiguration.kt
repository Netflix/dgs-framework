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

package com.netflix.graphql.dgs.springgraphql.autoconfig

import com.netflix.graphql.dgs.DataLoaderInstrumentationExtensionProvider
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataLoaderCustomizer
import com.netflix.graphql.dgs.DgsDataLoaderInstrumentation
import com.netflix.graphql.dgs.DgsDataLoaderOptionsProvider
import com.netflix.graphql.dgs.DgsDataLoaderReloadController
import com.netflix.graphql.dgs.DgsDefaultPreparsedDocumentProvider
import com.netflix.graphql.dgs.DgsExecutionResult
import com.netflix.graphql.dgs.DgsFederationResolver
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.DgsRuntimeWiring
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.ReloadSchemaIndicator
import com.netflix.graphql.dgs.autoconfig.DgsConfigurationProperties
import com.netflix.graphql.dgs.autoconfig.DgsDataloaderConfigurationProperties
import com.netflix.graphql.dgs.autoconfig.DgsInputArgumentConfiguration
import com.netflix.graphql.dgs.context.DgsCustomContextBuilder
import com.netflix.graphql.dgs.context.DgsCustomContextBuilderWithRequest
import com.netflix.graphql.dgs.context.GraphQLContextContributor
import com.netflix.graphql.dgs.context.GraphQLContextContributorInstrumentation
import com.netflix.graphql.dgs.exceptions.DefaultDataFetcherExceptionHandler
import com.netflix.graphql.dgs.internal.DataFetcherResultProcessor
import com.netflix.graphql.dgs.internal.DefaultDataLoaderOptionsProvider
import com.netflix.graphql.dgs.internal.DefaultDgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DefaultDgsDataLoaderReloadController
import com.netflix.graphql.dgs.internal.DefaultDgsGraphQLContextBuilder
import com.netflix.graphql.dgs.internal.DgsDataLoaderInstrumentationDataLoaderCustomizer
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsQueryExecutorRequestCustomizer
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.DgsWrapWithContextDataLoaderCustomizer
import com.netflix.graphql.dgs.internal.EntityFetcherRegistry
import com.netflix.graphql.dgs.internal.FlowDataFetcherResultProcessor
import com.netflix.graphql.dgs.internal.FluxDataFetcherResultProcessor
import com.netflix.graphql.dgs.internal.GraphQLJavaErrorInstrumentation
import com.netflix.graphql.dgs.internal.MonoDataFetcherResultProcessor
import com.netflix.graphql.dgs.internal.QueryValueCustomizer
import com.netflix.graphql.dgs.internal.ReloadableDgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.method.ArgumentResolver
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import com.netflix.graphql.dgs.mvc.internal.method.HandlerMethodArgumentResolverAdapter
import com.netflix.graphql.dgs.reactive.DgsReactiveCustomContextBuilderWithRequest
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveGraphQLContextBuilder
import com.netflix.graphql.dgs.reactive.internal.method.SyncHandlerMethodArgumentResolverAdapter
import com.netflix.graphql.dgs.springgraphql.DgsGraphQLSourceBuilder
import com.netflix.graphql.dgs.springgraphql.ReloadableGraphQLSource
import com.netflix.graphql.dgs.springgraphql.SpringGraphQLDgsQueryExecutor
import com.netflix.graphql.dgs.springgraphql.SpringGraphQLDgsReactiveQueryExecutor
import com.netflix.graphql.dgs.springgraphql.conditions.ConditionalOnDgsReload
import com.netflix.graphql.dgs.springgraphql.conditions.OnDgsReloadCondition
import com.netflix.graphql.dgs.springgraphql.webflux.DgsWebFluxGraphQLInterceptor
import com.netflix.graphql.dgs.springgraphql.webmvc.DgsWebMvcGraphQLInterceptor
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.ExecutionStrategy
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.preparsed.PreparsedDocumentProvider
import graphql.introspection.Introspection
import graphql.schema.DataFetcherFactory
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.TypeResolver
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeDefinitionRegistry
import io.micrometer.context.ContextRegistry
import io.micrometer.context.ContextSnapshotFactory
import io.micrometer.context.integration.Slf4jThreadLocalAccessor
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnJava
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.graphql.autoconfigure.GraphQlProperties
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.system.JavaVersion
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.core.task.support.ContextPropagatingTaskDecorator
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.execution.DataFetcherExceptionResolver
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.GraphQlSource
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import org.springframework.graphql.execution.SchemaReport
import org.springframework.graphql.execution.SelfDescribingDataFetcher
import org.springframework.graphql.execution.SubscriptionExceptionResolver
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter
import org.springframework.web.method.annotation.RequestHeaderMapMethodArgumentResolver
import org.springframework.web.method.annotation.RequestHeaderMethodArgumentResolver
import org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver
import org.springframework.web.reactive.BindingContext
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.result.method.annotation.CookieValueMethodArgumentResolver
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter
import org.springframework.web.servlet.mvc.method.annotation.ServletCookieValueMethodArgumentResolver
import org.springframework.web.servlet.mvc.method.annotation.ServletRequestDataBinderFactory
import java.time.Duration
import java.util.Optional
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * Framework autoconfiguration based on open source Spring only, without Netflix integrations.
 * This does NOT have logging, tracing, metrics and security integration.
 */
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@AutoConfiguration(
    beforeName = ["org.springframework.boot.graphql.autoconfigure.GraphQlAutoConfiguration"],
    afterName = ["org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration"],
)
@EnableConfigurationProperties(
    DgsSpringGraphQLConfigurationProperties::class,
    DgsConfigurationProperties::class,
    DgsDataloaderConfigurationProperties::class,
)
@ImportAutoConfiguration(classes = [JacksonAutoConfiguration::class, DgsInputArgumentConfiguration::class])
open class DgsSpringGraphQLAutoConfiguration(
    private val configProps: DgsConfigurationProperties,
    private val dataloaderConfigProps: DgsDataloaderConfigurationProperties,
) {
    companion object {
        const val AUTO_CONF_PREFIX = "dgs.graphql"
        private val LOG: Logger = LoggerFactory.getLogger(DgsSpringGraphQLAutoConfiguration::class.java)
    }

    @Bean
    @Order(PriorityOrdered.HIGHEST_PRECEDENCE)
    open fun graphQLContextContributionInstrumentation(
        graphQLContextContributors: ObjectProvider<GraphQLContextContributor>,
    ): Instrumentation = GraphQLContextContributorInstrumentation(graphQLContextContributors.orderedStream().toList())

    // This instrumentation needs to run before MetricsInstrumentation
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE - 1)
    @ConditionalOnProperty(
        prefix = "${AUTO_CONF_PREFIX}.errors.classification",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    open fun graphqlJavaErrorInstrumentation(): Instrumentation = GraphQLJavaErrorInstrumentation()

    @Bean
    @ConditionalOnMissingBean
    open fun defaultQueryValueCustomizer(): QueryValueCustomizer = QueryValueCustomizer { a -> a }

    @Bean
    @ConditionalOnMissingBean
    open fun dgsDataLoaderOptionsProvider(): DgsDataLoaderOptionsProvider = DefaultDataLoaderOptionsProvider()

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = ["dgsScheduledExecutorService"])
    @Qualifier("dgsScheduledExecutorService")
    open fun dgsScheduledExecutorService(): ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    @Bean
    @ConditionalOnProperty(
        prefix = "${AUTO_CONF_PREFIX}.convertAllDataLoadersToWithContext",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    @Order(0)
    open fun dgsWrapWithContextDataLoaderCustomizer(): DgsWrapWithContextDataLoaderCustomizer = DgsWrapWithContextDataLoaderCustomizer()

    @Bean
    @Order(100)
    open fun dgsDataLoaderInstrumentationDataLoaderCustomizer(
        instrumentations: List<DgsDataLoaderInstrumentation>,
    ): DgsDataLoaderInstrumentationDataLoaderCustomizer = DgsDataLoaderInstrumentationDataLoaderCustomizer(instrumentations)

    @Bean
    open fun dgsDataLoaderProvider(
        applicationContext: ApplicationContext,
        dataloaderOptionProvider: DgsDataLoaderOptionsProvider,
        @Qualifier("dgsScheduledExecutorService") dgsScheduledExecutorService: ScheduledExecutorService,
        extensionProviders: List<DataLoaderInstrumentationExtensionProvider>,
        customizers: List<DgsDataLoaderCustomizer>,
    ): DgsDataLoaderProvider =
        DefaultDgsDataLoaderProvider(
            applicationContext = applicationContext,
            extensionProviders = extensionProviders,
            customizers = customizers,
            dataLoaderOptionsProvider = dataloaderOptionProvider,
            scheduledExecutorService = dgsScheduledExecutorService,
            scheduleDuration = dataloaderConfigProps.scheduleDuration,
            enableTickerMode = dataloaderConfigProps.tickerModeEnabled,
        )

    /**
     * Autoconfiguration for DGS Data Loader reloading.
     *
     * This configuration is only activated when the 'dgs.reload' property is set to `true`.
     * It provides the necessary beans for data loader reloading, including:
     * - [ReloadableDgsDataLoaderProvider] that wraps the default provider implementation, the [DefaultDgsDataLoaderProvider].
     * - An implementation of a [DgsDataLoaderReloadController] that can be used ot force reloading of _Data Loaders_.
     *
     * **The reloading functionality is designed to be used primarily in development**,
     * it is discouraged to be used in production.
     */
    @AutoConfiguration
    @ConditionalOnDgsReload
    open class DgsDataLoaderReloadAutoConfiguration(
        private val dataloaderConfigProps: DgsDataloaderConfigurationProperties,
    ) {
        /**
         * Creates a [ReloadableDgsDataLoaderProvider] that wraps the standard [DgsDataLoaderProvider].
         *
         * This provider supports dynamic reloading of data loaders based on the [DgsDataLoaderReloadController].
         * It maintains the same interface as the standard provider but adds reload capabilities.
         *
         * The `@Primary` annotation ensures this bean takes precedence over the standard `DgsDataLoaderProvider`
         * when reload functionality is enabled.
         */
        @Bean
        @Primary
        open fun reloadableDgsDataLoaderProvider(
            applicationContext: ApplicationContext,
            dataLoaderOptionProvider: DgsDataLoaderOptionsProvider,
            @Qualifier("dgsScheduledExecutorService") dgsScheduledExecutorService: ScheduledExecutorService,
            extensionProviders: List<DataLoaderInstrumentationExtensionProvider>,
            customizers: List<DgsDataLoaderCustomizer>,
        ): ReloadableDgsDataLoaderProvider {
            LOG.info("Creating reloadable data loader provider with reload support enabled")
            return ReloadableDgsDataLoaderProvider(
                applicationContext = applicationContext,
                extensionProviders = extensionProviders,
                customizers = customizers,
                dataLoaderOptionsProvider = dataLoaderOptionProvider,
                scheduledExecutorService = dgsScheduledExecutorService,
                scheduleDuration = dataloaderConfigProps.scheduleDuration,
                enableTickerMode = dataloaderConfigProps.tickerModeEnabled,
            )
        }

        /**
         * Creates the default data loader reload controller.
         *
         * This controller provides a programmatic API for triggering data loader reloads
         * and accessing reload statistics. It's only available when reload functionality is enabled.
         *
         * @return DgsDataLoaderReloadController instance
         */
        @Bean
        @ConditionalOnMissingBean
        open fun dgsDataLoaderReloadController(
            reloadableDgsDataLoaderProvider: ReloadableDgsDataLoaderProvider,
        ): DgsDataLoaderReloadController {
            LOG.info("Creating data loader reload controller")
            // Get the actual ReloadableDgsDataLoaderProvider instance from the context
            return DefaultDgsDataLoaderReloadController(reloadableDgsDataLoaderProvider)
        }
    }

    @Bean
    open fun entityFetcherRegistry(): EntityFetcherRegistry = EntityFetcherRegistry()

    @Bean
    @ConditionalOnMissingBean
    open fun dataFetcherExceptionHandler(): DataFetcherExceptionHandler = DefaultDataFetcherExceptionHandler()

    @Bean
    @ConditionalOnProperty(
        prefix = "${AUTO_CONF_PREFIX}.preparsedDocumentProvider",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false,
    )
    @ConditionalOnMissingBean
    open fun preparsedDocumentProvider(configProps: DgsConfigurationProperties): PreparsedDocumentProvider =
        DgsDefaultPreparsedDocumentProvider(
            configProps.preparsedDocumentProvider.maximumCacheSize,
            Duration.parse(configProps.preparsedDocumentProvider.cacheValidityDuration),
        )

    @Bean
    @ConditionalOnMissingBean
    open fun graphQLContextBuilder(
        dgsCustomContextBuilder: Optional<DgsCustomContextBuilder<*>>,
        dgsCustomContextBuilderWithRequest: Optional<DgsCustomContextBuilderWithRequest<*>>,
    ): DefaultDgsGraphQLContextBuilder = DefaultDgsGraphQLContextBuilder(dgsCustomContextBuilder, dgsCustomContextBuilderWithRequest)

    /**
     * Used by the [ReloadableGraphQLSource], it controls if, and when, such executor should reload the schema.
     * This implementation will return either the boolean value of the `dgs.reload` flag
     * or `true` if the `laptop` profile is an active Spring Boot profiles.
     * <p>
     * You can provide a bean of type [ReloadSchemaIndicator] if you want to control when the
     * [ReloadableGraphQLSource] should reload the schema.
     *
     * @implSpec the implementation of such bean should be thread-safe.
     */
    @Bean
    @ConditionalOnMissingBean
    open fun defaultReloadSchemaIndicator(environment: Environment): ReloadSchemaIndicator {
        val hotReloadSetting = OnDgsReloadCondition.evaluate(environment)
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
        dataFetcherResultProcessors: List<DataFetcherResultProcessor>,
        dataFetcherExceptionHandler: Optional<DataFetcherExceptionHandler> = Optional.empty(),
        entityFetcherRegistry: EntityFetcherRegistry,
        defaultDataFetcherFactory: Optional<DataFetcherFactory<*>> = Optional.empty(),
        methodDataFetcherFactory: MethodDataFetcherFactory,
        fallbackTypeResolver: TypeResolver? = null,
    ): DgsSchemaProvider =
        DgsSchemaProvider(
            applicationContext = applicationContext,
            federationResolver = federationResolver,
            existingTypeDefinitionRegistry = existingTypeDefinitionFactory,
            schemaLocations = configProps.schemaLocations,
            dataFetcherResultProcessors = dataFetcherResultProcessors,
            dataFetcherExceptionHandler = dataFetcherExceptionHandler,
            entityFetcherRegistry = entityFetcherRegistry,
            defaultDataFetcherFactory = defaultDataFetcherFactory,
            methodDataFetcherFactory = methodDataFetcherFactory,
            schemaWiringValidationEnabled = configProps.schemaWiringValidationEnabled,
            enableEntityFetcherCustomScalarParsing = configProps.enableEntityFetcherCustomScalarParsing,
            fallbackTypeResolver = fallbackTypeResolver,
            enableStrictMode = configProps.strictMode.enabled,
        )

    @Bean
    open fun graphQlSource(
        properties: GraphQlProperties,
        dgsSchemaProvider: DgsSchemaProvider,
        exceptionResolvers: ObjectProvider<DataFetcherExceptionResolver>,
        subscriptionExceptionResolvers: ObjectProvider<SubscriptionExceptionResolver>,
        instrumentations: ObjectProvider<Instrumentation>,
        wiringConfigurers: ObjectProvider<RuntimeWiringConfigurer>,
        sourceCustomizers: ObjectProvider<GraphQlSourceBuilderCustomizer>,
        reloadSchemaIndicator: ReloadSchemaIndicator,
        defaultExceptionHandler: DataFetcherExceptionHandler,
        reportConsumer: Consumer<SchemaReport>?,
    ): GraphQlSource {
        val dataFetcherExceptionResolvers: MutableList<DataFetcherExceptionResolver> =
            exceptionResolvers
                .orderedStream()
                .collect(Collectors.toList())
        dataFetcherExceptionResolvers += ExceptionHandlerResolverAdapter(defaultExceptionHandler)

        val builder =
            DgsGraphQLSourceBuilder(dgsSchemaProvider, configProps.introspection.showSdlComments)
                .exceptionResolvers(dataFetcherExceptionResolvers)
                .subscriptionExceptionResolvers(subscriptionExceptionResolvers.orderedStream().toList())
                .instrumentation(instrumentations.orderedStream().toList())

        if (properties.schema.inspection.isEnabled) {
            if (reportConsumer != null) {
                builder.inspectSchemaMappings(reportConsumer)
            } else if (LOG.isInfoEnabled) {
                builder.inspectSchemaMappings { schemaReport ->
                    val messageBuilder = StringBuilder("***Schema Report***\n")

                    val arguments =
                        schemaReport.unmappedArguments().map { entry ->
                            val (key, value) = entry
                            if (key is SelfDescribingDataFetcher) {
                                val dataFetcher =
                                    (key as DgsGraphQLSourceBuilder.DgsSelfDescribingDataFetcher).dataFetcher
                                dataFetcher.method.declaringClass.name + "." + dataFetcher.method.name + " for arguments " + value
                            } else {
                                entry.toString()
                            }
                        }

                    messageBuilder.append("Unmapped fields: ").append(schemaReport.unmappedFields()).append('\n')
                    messageBuilder.append("Unmapped registrations: ").append(schemaReport.unmappedRegistrations()).append('\n')
                    messageBuilder.append("Unmapped arguments: ").append(arguments).append('\n')
                    messageBuilder.append("Skipped types: ").append(schemaReport.skippedTypes()).append('\n')

                    LOG.info("{}", messageBuilder)
                }
            }
        }

        wiringConfigurers.orderedStream().forEach { configurer: RuntimeWiringConfigurer ->
            builder.configureRuntimeWiring(configurer)
        }
        sourceCustomizers.orderedStream().forEach { customizer: GraphQlSourceBuilderCustomizer ->
            customizer.customize(builder)
        }
        return ReloadableGraphQLSource(builder, reloadSchemaIndicator)
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = ["reactor.core.publisher.Mono"])
    open fun monoReactiveDataFetcherResultProcessor(): MonoDataFetcherResultProcessor = MonoDataFetcherResultProcessor()

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = ["kotlinx.coroutines.flow.Flow"])
    open fun flowReactiveDataFetcherResultProcessor(): FlowDataFetcherResultProcessor = FlowDataFetcherResultProcessor()

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = ["reactor.core.publisher.Flux"])
    open fun fluxReactiveDataFetcherResultProcessor(): FluxDataFetcherResultProcessor = FluxDataFetcherResultProcessor()

    /**
     * JDK 21+ only - Creates the dgsAsyncTaskExecutor which is used to run data fetchers automatically wrapped in CompletableFuture.
     * Can be provided by other frameworks to enable context propagation.
     */
    @Bean
    @Qualifier("dgsAsyncTaskExecutor")
    @ConditionalOnJava(value = JavaVersion.TWENTY_ONE)
    @ConditionalOnMissingBean(name = ["dgsAsyncTaskExecutor"])
    @ConditionalOnProperty(prefix = "${AUTO_CONF_PREFIX}.virtualthreads", name = ["enabled"], havingValue = "true", matchIfMissing = false)
    open fun virtualThreadsTaskExecutor(): AsyncTaskExecutor {
        LOG.info("Enabling virtual threads for DGS")

        val contextRegistry =
            ContextRegistry()
                .loadContextAccessors()
                .loadThreadLocalAccessors()
                .registerThreadLocalAccessor(Slf4jThreadLocalAccessor())

        val executor = SimpleAsyncTaskExecutor("dgs-virtual-thread-")
        executor.setVirtualThreads(true)
        executor.setTaskDecorator(
            ContextPropagatingTaskDecorator(ContextSnapshotFactory.builder().contextRegistry(contextRegistry).build()),
        )
        return executor
    }

    @Bean
    open fun methodDataFetcherFactory(
        argumentResolvers: ObjectProvider<ArgumentResolver>,
        @Qualifier("dgsAsyncTaskExecutor") taskExecutorOptional: Optional<AsyncTaskExecutor>,
    ): MethodDataFetcherFactory {
        val taskExecutor =
            if (taskExecutorOptional.isPresent) {
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
            override fun apply(
                request: WebRequest?,
                headers: HttpHeaders?,
            ): WebRequest? {
                if (headers == null || headers.isEmpty || request !is NativeWebRequest) {
                    return request
                }
                val mockRequest =
                    request.nativeRequest as? MockHttpServletRequest
                        ?: return request
                headers.forEach { key, value ->
                    if (mockRequest.getHeader(key) == null) {
                        mockRequest.addHeader(key, value)
                    }
                }
                return request
            }

            override fun toString(): String = "{MockRequestHeaderCustomizer}"
        }
    }

    @Bean
    @DgsComponent
    open fun dgsRuntimeWiringConfigurerBridge(configurers: List<RuntimeWiringConfigurer>): DgsRuntimeWiringConfigurerBridge =
        DgsRuntimeWiringConfigurerBridge(configurers)

    class DgsRuntimeWiringConfigurerBridge(
        private val configurers: List<RuntimeWiringConfigurer>,
    ) {
        @DgsRuntimeWiring
        fun runtimeWiring(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
            configurers.forEach {
                it.configure(builder)
            }
            return builder
        }
    }

    @Bean
    @ConditionalOnProperty("dgs.springgraphql.pagination.enabled", havingValue = "true", matchIfMissing = true)
    @DgsComponent
    open fun dgsTypeDefinitionConfigurerBridge(environment: Environment): DgsTypeDefinitionConfigurerBridge =
        DgsTypeDefinitionConfigurerBridge()

    class DgsTypeDefinitionConfigurerBridge {
        @DgsTypeDefinitionRegistry
        fun typeDefinitionRegistry(typeDefinitionRegistry: TypeDefinitionRegistry): TypeDefinitionRegistry {
            val newTypeDefinitionRegistry = TypeDefinitionRegistry()
            // TODO (SBN4) Spring GraphQL 2.0 automatically adds ConnectionTypeDefinitionConfigurer
            // in GraphQlAutoConfiguration.graphQlSource(), so we no longer need to add it here.
            // Previously this was needed, but now it causes duplicate MessageConnection and MessageEdge types.
            // ConnectionTypeDefinitionConfigurer().configure(typeDefinitionRegistry)
            return newTypeDefinitionRegistry
        }
    }

    @Bean
    open fun sourceBuilderCustomizer(
        preparsedDocumentProvider: Optional<PreparsedDocumentProvider>,
        @Qualifier("query") providedQueryExecutionStrategy: Optional<ExecutionStrategy>,
        @Qualifier("mutation") providedMutationExecutionStrategy: Optional<ExecutionStrategy>,
        dataFetcherExceptionHandler: DataFetcherExceptionHandler,
        environment: Environment,
    ): GraphQlSourceBuilderCustomizer =
        GraphQlSourceBuilderCustomizer { builder ->
            builder.configureGraphQl { graphQlBuilder ->
                val apqEnabled = environment.getProperty("dgs.graphql.apq.enabled", Boolean::class.java, false)
                // If apq is enabled, we will not use this preparsedDocumentProvider and use DgsAPQPreparsedDocumentProviderWrapper instead
                if (preparsedDocumentProvider.isPresent && !apqEnabled) {
                    graphQlBuilder.preparsedDocumentProvider(preparsedDocumentProvider.get())
                }

                if (providedQueryExecutionStrategy.isPresent) {
                    graphQlBuilder
                        .queryExecutionStrategy(providedQueryExecutionStrategy.get())
                }

                if (providedMutationExecutionStrategy.isPresent) {
                    graphQlBuilder
                        .mutationExecutionStrategy(providedMutationExecutionStrategy.get())
                }
            }
        }

    @Bean
    @ConditionalOnProperty(
        name = ["spring.graphql.schema.introspection.enabled"],
        havingValue = "false",
        matchIfMissing = false,
    )
    open fun disableIntrospectionContextContributor(): GraphQLContextContributor =
        GraphQLContextContributor {
            builder,
            _,
            _,
            ->
            builder.put(Introspection.INTROSPECTION_DISABLED, true)
        }

    @Bean
    open fun springGraphQLDgsQueryExecutor(
        executionService: ExecutionGraphQlService,
        dgsContextBuilder: DefaultDgsGraphQLContextBuilder,
        dgsDataLoaderProvider: DgsDataLoaderProvider,
        requestCustomizer: ObjectProvider<DgsQueryExecutorRequestCustomizer>,
        graphQLContextContributors: List<GraphQLContextContributor>,
    ): DgsQueryExecutor =
        SpringGraphQLDgsQueryExecutor(
            executionService,
            dgsContextBuilder,
            dgsDataLoaderProvider,
            requestCustomizer = requestCustomizer.getIfAvailable(DgsQueryExecutorRequestCustomizer::DEFAULT_REQUEST_CUSTOMIZER),
            graphQLContextContributors,
        )

    /**
     * Backward compatibility for setting response headers through a "dgs-response-headers" field in extensions, or using DgsExecutionResult.
     * While this can easily be done through a custom WebGraphQlInterceptor, this bean provides backward compatibility with older code.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "${AUTO_CONF_PREFIX}.dgs-response-headers",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    open fun dgsHeadersInterceptor(): WebGraphQlInterceptor =
        WebGraphQlInterceptor { request, chain ->
            chain.next(request).doOnNext { response: WebGraphQlResponse ->
                val responseHeadersExtension = response.extensions["dgs-response-headers"]
                if (responseHeadersExtension is HttpHeaders) {
                    response.responseHeaders.addAll(responseHeadersExtension)
                }
                if (response.executionResult is DgsExecutionResult) {
                    response.responseHeaders.addAll((response.executionResult as DgsExecutionResult).headers)
                }
            }
        }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    open class WebMvcConfiguration(
        private val dgsSpringGraphQLConfigurationProperties: DgsSpringGraphQLConfigurationProperties,
    ) {
        @Bean
        open fun dgsGraphQlInterceptor(
            dgsDataLoaderProvider: DgsDataLoaderProvider,
            dgsDefaultContextBuilder: DefaultDgsGraphQLContextBuilder,
            graphQLContextContributors: List<GraphQLContextContributor>,
        ): DgsWebMvcGraphQLInterceptor =
            DgsWebMvcGraphQLInterceptor(
                dgsDataLoaderProvider,
                dgsDefaultContextBuilder,
                dgsSpringGraphQLConfigurationProperties,
                graphQLContextContributors,
            )
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    open class WebMvcArgumentHandlerConfiguration {
        @Qualifier
        private annotation class Dgs

        @Bean
        @Dgs
        open fun dgsWebDataBinderFactory(
            @Qualifier("requestMappingHandlerAdapter") adapter: ObjectProvider<RequestMappingHandlerAdapter>,
        ): WebDataBinderFactory = ServletRequestDataBinderFactory(listOf(), adapter.ifAvailable?.webBindingInitializer)

        @Bean
        open fun requestHeaderMapResolver(
            @Dgs dataBinderFactory: WebDataBinderFactory,
        ): ArgumentResolver = HandlerMethodArgumentResolverAdapter(RequestHeaderMapMethodArgumentResolver(), dataBinderFactory)

        @Bean
        open fun requestHeaderResolver(
            beanFactory: ConfigurableBeanFactory,
            @Dgs dataBinderFactory: WebDataBinderFactory,
        ): ArgumentResolver =
            HandlerMethodArgumentResolverAdapter(
                RequestHeaderMethodArgumentResolver(beanFactory),
                dataBinderFactory,
            )

        @Bean
        open fun requestParamResolver(
            @Dgs dataBinderFactory: WebDataBinderFactory,
        ): ArgumentResolver = HandlerMethodArgumentResolverAdapter(RequestParamMethodArgumentResolver(false), dataBinderFactory)

        @Bean
        open fun requestParamMapResolver(
            @Dgs dataBinderFactory: WebDataBinderFactory,
        ): ArgumentResolver = HandlerMethodArgumentResolverAdapter(RequestParamMapMethodArgumentResolver(), dataBinderFactory)

        @Bean
        open fun cookieValueResolver(
            beanFactory: ConfigurableBeanFactory,
            @Dgs dataBinderFactory: WebDataBinderFactory,
        ): ArgumentResolver =
            HandlerMethodArgumentResolverAdapter(
                ServletCookieValueMethodArgumentResolver(beanFactory),
                dataBinderFactory,
            )
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Publisher::class, ServerRequest::class)
    open class ReactiveConfiguration {
        @Bean
        open fun springGraphQLDgsReactiveQueryExecutor(
            executionService: ExecutionGraphQlService,
            dgsContextBuilder: DefaultDgsReactiveGraphQLContextBuilder,
            dgsDataLoaderProvider: DgsDataLoaderProvider,
        ): DgsReactiveQueryExecutor = SpringGraphQLDgsReactiveQueryExecutor(executionService, dgsContextBuilder, dgsDataLoaderProvider)

        @Bean
        @ConditionalOnMissingBean
        open fun reactiveGraphQlContextBuilder(
            dgsReactiveCustomContextBuilderWithRequest: Optional<DgsReactiveCustomContextBuilderWithRequest<*>>,
        ): DefaultDgsReactiveGraphQLContextBuilder = DefaultDgsReactiveGraphQLContextBuilder(dgsReactiveCustomContextBuilderWithRequest)

        @Bean
        @ConditionalOnMissingBean
        open fun dgsServerWebExchangeContextFilter(): ServerWebExchangeContextFilter = ServerWebExchangeContextFilter()
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    open class WebFluxConfiguration {
        @Bean
        open fun webFluxDgsGraphQLInterceptor(
            dgsDataLoaderProvider: DgsDataLoaderProvider,
            defaultDgsReactiveGraphQLContextBuilder: DefaultDgsReactiveGraphQLContextBuilder,
        ): DgsWebFluxGraphQLInterceptor =
            DgsWebFluxGraphQLInterceptor(
                dgsDataLoaderProvider,
                defaultDgsReactiveGraphQLContextBuilder,
            )
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    open class WebFluxArgumentHandlerConfiguration {
        @Qualifier
        private annotation class Dgs

        @Dgs
        @Bean
        open fun dgsBindingContext(
            adapter: ObjectProvider<org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter>,
        ): BindingContext = BindingContext(adapter.ifAvailable?.webBindingInitializer)

        @Bean
        open fun cookieValueArgumentResolver(
            beanFactory: ConfigurableBeanFactory,
            registry: ReactiveAdapterRegistry,
            @Dgs bindingContext: BindingContext,
        ): ArgumentResolver =
            SyncHandlerMethodArgumentResolverAdapter(
                CookieValueMethodArgumentResolver(beanFactory, registry),
                bindingContext,
            )

        @Bean
        open fun requestHeaderMapArgumentResolver(
            registry: ReactiveAdapterRegistry,
            @Dgs bindingContext: BindingContext,
        ): ArgumentResolver =
            SyncHandlerMethodArgumentResolverAdapter(
                org.springframework.web.reactive.result.method.annotation.RequestHeaderMapMethodArgumentResolver(
                    registry,
                ),
                bindingContext,
            )

        @Bean
        open fun requestHeaderArgumentResolver(
            beanFactory: ConfigurableBeanFactory,
            registry: ReactiveAdapterRegistry,
            @Dgs bindingContext: BindingContext,
        ): ArgumentResolver =
            SyncHandlerMethodArgumentResolverAdapter(
                org.springframework.web.reactive.result.method.annotation.RequestHeaderMethodArgumentResolver(
                    beanFactory,
                    registry,
                ),
                bindingContext,
            )

        @Bean
        open fun requestParamArgumentResolver(
            beanFactory: ConfigurableBeanFactory,
            registry: ReactiveAdapterRegistry,
            @Dgs bindingContext: BindingContext,
        ): ArgumentResolver =
            SyncHandlerMethodArgumentResolverAdapter(
                org.springframework.web.reactive.result.method.annotation.RequestParamMethodArgumentResolver(
                    beanFactory,
                    registry,
                    false,
                ),
                bindingContext,
            )

        @Bean
        open fun requestParamMapArgumentResolver(
            beanFactory: ConfigurableBeanFactory,
            registry: ReactiveAdapterRegistry,
            @Dgs bindingContext: BindingContext,
        ): ArgumentResolver =
            SyncHandlerMethodArgumentResolverAdapter(
                org.springframework.web.reactive.result.method.annotation
                    .RequestParamMapMethodArgumentResolver(registry),
                bindingContext,
            )
    }
}

class ExceptionHandlerResolverAdapter(
    private val dataFetcherExceptionHandler: DataFetcherExceptionHandler,
) : DataFetcherExceptionResolverAdapter() {
    override fun resolveToMultipleErrors(
        ex: Throwable,
        env: DataFetchingEnvironment,
    ): MutableList<GraphQLError>? {
        val exceptionHandlerParameters =
            DataFetcherExceptionHandlerParameters
                .newExceptionParameters()
                .exception(ex)
                .dataFetchingEnvironment(env)
                .build()

        return dataFetcherExceptionHandler.handleException(exceptionHandlerParameters).get().errors
    }
}
