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

package com.netflix.graphql.dgs.springgraphql.autoconfig;

import com.netflix.graphql.dgs.DataLoaderInstrumentationExtensionProvider;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsDataLoaderCustomizer;
import com.netflix.graphql.dgs.DgsDataLoaderInstrumentation;
import com.netflix.graphql.dgs.DgsDataLoaderOptionsProvider;
import com.netflix.graphql.dgs.DgsDataLoaderReloadController;
import com.netflix.graphql.dgs.DgsDefaultPreparsedDocumentProvider;
import com.netflix.graphql.dgs.DgsExecutionResult;
import com.netflix.graphql.dgs.DgsFederationResolver;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.DgsRuntimeWiring;
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry;
import com.netflix.graphql.dgs.ReloadSchemaIndicator;
import com.netflix.graphql.dgs.autoconfig.DgsConfigurationProperties;
import com.netflix.graphql.dgs.autoconfig.DgsDataloaderConfigurationProperties;
import com.netflix.graphql.dgs.autoconfig.DgsInputArgumentConfiguration;
import com.netflix.graphql.dgs.context.DgsCustomContextBuilder;
import com.netflix.graphql.dgs.context.DgsCustomContextBuilderWithRequest;
import com.netflix.graphql.dgs.context.GraphQLContextContributor;
import com.netflix.graphql.dgs.context.GraphQLContextContributorInstrumentation;
import com.netflix.graphql.dgs.diagnostics.DgsJsonMapperMissingException;
import com.netflix.graphql.dgs.exceptions.DefaultDataFetcherExceptionHandler;
import com.netflix.graphql.dgs.internal.DataFetcherResultProcessor;
import com.netflix.graphql.dgs.internal.DefaultDataLoaderOptionsProvider;
import com.netflix.graphql.dgs.internal.DefaultDgsDataLoaderProvider;
import com.netflix.graphql.dgs.internal.DefaultDgsDataLoaderReloadController;
import com.netflix.graphql.dgs.internal.DefaultDgsGraphQLContextBuilder;
import com.netflix.graphql.dgs.internal.DgsDataLoaderInstrumentationDataLoaderCustomizer;
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider;
import com.netflix.graphql.dgs.internal.DgsQueryExecutorRequestCustomizer;
import com.netflix.graphql.dgs.internal.DgsSchemaProvider;
import com.netflix.graphql.dgs.internal.DgsWrapWithContextDataLoaderCustomizer;
import com.netflix.graphql.dgs.internal.EntityFetcherRegistry;
import com.netflix.graphql.dgs.internal.FlowDataFetcherResultProcessor;
import com.netflix.graphql.dgs.internal.FluxDataFetcherResultProcessor;
import com.netflix.graphql.dgs.internal.GraphQLJavaErrorInstrumentation;
import com.netflix.graphql.dgs.internal.Jackson3DgsJsonMapper;
import com.netflix.graphql.dgs.internal.MonoDataFetcherResultProcessor;
import com.netflix.graphql.dgs.internal.QueryValueCustomizer;
import com.netflix.graphql.dgs.internal.ReloadableDgsDataLoaderProvider;
import com.netflix.graphql.dgs.internal.method.ArgumentResolver;
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory;
import com.netflix.graphql.dgs.json.DgsJsonMapper;
import com.netflix.graphql.dgs.mvc.internal.method.HandlerMethodArgumentResolverAdapter;
import com.netflix.graphql.dgs.reactive.DgsReactiveCustomContextBuilderWithRequest;
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor;
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveGraphQLContextBuilder;
import com.netflix.graphql.dgs.reactive.internal.method.SyncHandlerMethodArgumentResolverAdapter;
import com.netflix.graphql.dgs.springgraphql.DgsGraphQLSourceBuilder;
import com.netflix.graphql.dgs.springgraphql.ReloadableGraphQLSource;
import com.netflix.graphql.dgs.springgraphql.SpringGraphQLDgsQueryExecutor;
import com.netflix.graphql.dgs.springgraphql.SpringGraphQLDgsReactiveQueryExecutor;
import com.netflix.graphql.dgs.springgraphql.conditions.ConditionalOnDgsReload;
import com.netflix.graphql.dgs.springgraphql.conditions.OnDgsReloadCondition;
import com.netflix.graphql.dgs.springgraphql.webflux.DgsWebFluxGraphQLInterceptor;
import com.netflix.graphql.dgs.springgraphql.webmvc.DgsWebMvcGraphQLInterceptor;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.ExecutionStrategy;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.introspection.Introspection;
import graphql.schema.DataFetcherFactory;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.Dispatchers;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnJava;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.graphql.autoconfigure.GraphQlProperties;
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.boot.system.JavaVersion;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.execution.ConnectionTypeDefinitionConfigurer;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.execution.SchemaReport;
import org.springframework.graphql.execution.SelfDescribingDataFetcher;
import org.springframework.graphql.execution.SubscriptionExceptionResolver;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.method.annotation.RequestHeaderMapMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestHeaderMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.result.method.annotation.CookieValueMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ServletCookieValueMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.ServletRequestDataBinderFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/**
 * Framework autoconfiguration based on open source Spring only, without Netflix integrations.
 * This does NOT have logging, tracing, metrics and security integration.
 */
@AutoConfiguration(
        beforeName = "org.springframework.boot.graphql.autoconfigure.GraphQlAutoConfiguration",
        afterName = {
            "org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration",
            "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
            "org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration"
        })
@EnableConfigurationProperties({
    DgsSpringGraphQLConfigurationProperties.class,
    DgsConfigurationProperties.class,
    DgsDataloaderConfigurationProperties.class
})
@ImportAutoConfiguration(classes = DgsInputArgumentConfiguration.class)
public class DgsSpringGraphQLAutoConfiguration {
    @NotNull
    public static final String AUTO_CONF_PREFIX = "dgs.graphql";

    private static final Logger LOG = LoggerFactory.getLogger(DgsSpringGraphQLAutoConfiguration.class);

    private final DgsConfigurationProperties configProps;
    private final DgsDataloaderConfigurationProperties dataloaderConfigProps;

    public DgsSpringGraphQLAutoConfiguration(
            @NotNull DgsConfigurationProperties configProps, @NotNull DgsDataloaderConfigurationProperties dataloaderConfigProps) {
        this.configProps = configProps;
        this.dataloaderConfigProps = dataloaderConfigProps;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "tools.jackson.databind.json.JsonMapper")
    @ConditionalOnProperty(
            name = "dgs.graphql.preferred-json-mapper",
            havingValue = "jackson3",
            matchIfMissing = true)
    static class Jackson3DgsJsonMapperConfiguration {
        @NotNull
        @Bean
        @ConditionalOnMissingBean(DgsJsonMapper.class)
        public DgsJsonMapper dgsJsonMapper() {
            return new Jackson3DgsJsonMapper();
        }
    }

    @NotNull
    @Bean
    @ConditionalOnMissingBean(DgsJsonMapper.class)
    public DgsJsonMapper dgsJsonMapperFallback() {
        throw new DgsJsonMapperMissingException();
    }

    @NotNull
    @Bean
    @Order(PriorityOrdered.HIGHEST_PRECEDENCE)
    public Instrumentation graphQLContextContributionInstrumentation(
            @NotNull ObjectProvider<GraphQLContextContributor> graphQLContextContributors) {
        return new GraphQLContextContributorInstrumentation(
                graphQLContextContributors.orderedStream().toList());
    }

    // This instrumentation needs to run before MetricsInstrumentation
    @NotNull
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE - 1)
    @ConditionalOnProperty(
            prefix = AUTO_CONF_PREFIX + ".errors.classification",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public Instrumentation graphqlJavaErrorInstrumentation() {
        return new GraphQLJavaErrorInstrumentation();
    }

    @NotNull
    @Bean
    @ConditionalOnMissingBean
    public QueryValueCustomizer defaultQueryValueCustomizer() {
        return query -> query;
    }

    @NotNull
    @Bean
    @ConditionalOnMissingBean
    public DgsDataLoaderOptionsProvider dgsDataLoaderOptionsProvider() {
        return new DefaultDataLoaderOptionsProvider();
    }

    @NotNull
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "dgsScheduledExecutorService")
    @Qualifier("dgsScheduledExecutorService")
    public ScheduledExecutorService dgsScheduledExecutorService() {
        return Executors.newSingleThreadScheduledExecutor();
    }

    @NotNull
    @Bean
    @ConditionalOnProperty(
            prefix = AUTO_CONF_PREFIX + ".convertAllDataLoadersToWithContext",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @Order(0)
    public DgsWrapWithContextDataLoaderCustomizer dgsWrapWithContextDataLoaderCustomizer() {
        return new DgsWrapWithContextDataLoaderCustomizer();
    }

    @NotNull
    @Bean
    @Order(100)
    public DgsDataLoaderInstrumentationDataLoaderCustomizer dgsDataLoaderInstrumentationDataLoaderCustomizer(
            @NotNull List<? extends DgsDataLoaderInstrumentation> instrumentations) {
        return new DgsDataLoaderInstrumentationDataLoaderCustomizer(instrumentations);
    }

    @NotNull
    @Bean
    public DefaultDgsDataLoaderProvider dgsDataLoaderProvider(
            @NotNull ApplicationContext applicationContext,
            @NotNull DgsDataLoaderOptionsProvider dataloaderOptionProvider,
            @NotNull @Qualifier("dgsScheduledExecutorService") ScheduledExecutorService dgsScheduledExecutorService,
            @NotNull List<? extends DataLoaderInstrumentationExtensionProvider> extensionProviders,
            @NotNull List<? extends DgsDataLoaderCustomizer> customizers) {
        return new DefaultDgsDataLoaderProvider(
                applicationContext,
                extensionProviders,
                customizers,
                dataloaderOptionProvider,
                dgsScheduledExecutorService,
                dataloaderConfigProps.getScheduleDuration(),
                dataloaderConfigProps.isTickerModeEnabled());
    }

    /**
     * Autoconfiguration for DGS Data Loader reloading.
     *
     * <p>This configuration is only activated when the 'dgs.reload' property is set to {@code true}.
     *
     * <p><strong>The reloading functionality is designed to be used primarily in development</strong>,
     * it is discouraged to be used in production.
     */
    @AutoConfiguration
    @ConditionalOnDgsReload
    public static class DgsDataLoaderReloadAutoConfiguration {
        private final DgsDataloaderConfigurationProperties dataloaderConfigProps;

        public DgsDataLoaderReloadAutoConfiguration(@NotNull DgsDataloaderConfigurationProperties dataloaderConfigProps) {
            this.dataloaderConfigProps = dataloaderConfigProps;
        }

        /**
         * Creates a {@link ReloadableDgsDataLoaderProvider} that wraps the standard {@link DgsDataLoaderProvider}.
         *
         * <p>The {@code @Primary} annotation ensures this bean takes precedence over the standard
         * {@code DgsDataLoaderProvider} when reload functionality is enabled.
         */
        @NotNull
        @Bean
        @Primary
        public ReloadableDgsDataLoaderProvider reloadableDgsDataLoaderProvider(
                @NotNull ApplicationContext applicationContext,
                @NotNull DgsDataLoaderOptionsProvider dataLoaderOptionProvider,
                @NotNull @Qualifier("dgsScheduledExecutorService") ScheduledExecutorService dgsScheduledExecutorService,
                @NotNull List<? extends DataLoaderInstrumentationExtensionProvider> extensionProviders,
                @NotNull List<? extends DgsDataLoaderCustomizer> customizers) {
            LOG.info("Creating reloadable data loader provider with reload support enabled");
            return new ReloadableDgsDataLoaderProvider(
                    applicationContext,
                    dgsScheduledExecutorService,
                    extensionProviders,
                    customizers,
                    dataLoaderOptionProvider,
                    dataloaderConfigProps.getScheduleDuration(),
                    dataloaderConfigProps.isTickerModeEnabled());
        }

        /**
         * Creates the default data loader reload controller.
         *
         * @return DgsDataLoaderReloadController instance
         */
        @NotNull
        @Bean
        @ConditionalOnMissingBean
        public DgsDataLoaderReloadController dgsDataLoaderReloadController(
                @NotNull ReloadableDgsDataLoaderProvider reloadableDgsDataLoaderProvider) {
            LOG.info("Creating data loader reload controller");
            return new DefaultDgsDataLoaderReloadController(reloadableDgsDataLoaderProvider);
        }
    }

    @NotNull
    @Bean
    public EntityFetcherRegistry entityFetcherRegistry() {
        return new EntityFetcherRegistry();
    }

    @NotNull
    @Bean
    @ConditionalOnMissingBean
    public DataFetcherExceptionHandler dataFetcherExceptionHandler() {
        return new DefaultDataFetcherExceptionHandler();
    }

    @NotNull
    @Bean
    @ConditionalOnProperty(
            prefix = AUTO_CONF_PREFIX + ".preparsedDocumentProvider",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false)
    @ConditionalOnMissingBean
    public PreparsedDocumentProvider preparsedDocumentProvider(@NotNull DgsConfigurationProperties configProps) {
        return new DgsDefaultPreparsedDocumentProvider(
                configProps.getPreparsedDocumentProvider().getMaximumCacheSize(),
                Duration.parse(configProps.getPreparsedDocumentProvider().getCacheValidityDuration()));
    }

    @NotNull
    @Bean
    @ConditionalOnMissingBean
    public DefaultDgsGraphQLContextBuilder graphQLContextBuilder(
            @NotNull Optional<DgsCustomContextBuilder<?>> dgsCustomContextBuilder,
            @NotNull Optional<DgsCustomContextBuilderWithRequest<?>> dgsCustomContextBuilderWithRequest) {
        return new DefaultDgsGraphQLContextBuilder(dgsCustomContextBuilder, dgsCustomContextBuilderWithRequest);
    }

    /**
     * Used by the {@link ReloadableGraphQLSource}, it controls if, and when, such executor should reload the schema.
     * This implementation will return either the boolean value of the {@code dgs.reload} flag
     * or {@code true} if the {@code laptop} profile is an active Spring Boot profile.
     */
    @NotNull
    @Bean
    @ConditionalOnMissingBean
    public ReloadSchemaIndicator defaultReloadSchemaIndicator(@NotNull Environment environment) {
        boolean hotReloadSetting = OnDgsReloadCondition.evaluate(environment);
        return () -> hotReloadSetting;
    }

    @Bean
    @ConditionalOnMissingBean
    public DgsSchemaProvider dgsSchemaProvider(
            ApplicationContext applicationContext,
            Optional<DgsFederationResolver> federationResolver,
            Optional<TypeDefinitionRegistry> existingTypeDefinitionFactory,
            Optional<GraphQLCodeRegistry> existingCodeRegistry,
            List<? extends DataFetcherResultProcessor> dataFetcherResultProcessors,
            Optional<DataFetcherExceptionHandler> dataFetcherExceptionHandler,
            EntityFetcherRegistry entityFetcherRegistry,
            Optional<DataFetcherFactory<?>> defaultDataFetcherFactory,
            MethodDataFetcherFactory methodDataFetcherFactory,
            Optional<TypeResolver> fallbackTypeResolver) {
        return new DgsSchemaProvider(
                applicationContext,
                federationResolver,
                existingTypeDefinitionFactory,
                configProps.getSchemaLocations(),
                dataFetcherResultProcessors,
                dataFetcherExceptionHandler,
                entityFetcherRegistry,
                defaultDataFetcherFactory,
                methodDataFetcherFactory,
                null,
                configProps.isSchemaWiringValidationEnabled(),
                configProps.isEnableEntityFetcherCustomScalarParsing(),
                fallbackTypeResolver.orElse(null),
                configProps.getStrictMode().isEnabled(),
                configProps.getFederation().isEnabled());
    }

    @Bean
    public GraphQlSource graphQlSource(
            GraphQlProperties properties,
            DgsSchemaProvider dgsSchemaProvider,
            ObjectProvider<DataFetcherExceptionResolver> exceptionResolvers,
            ObjectProvider<SubscriptionExceptionResolver> subscriptionExceptionResolvers,
            ObjectProvider<Instrumentation> instrumentations,
            ObjectProvider<RuntimeWiringConfigurer> wiringConfigurers,
            ObjectProvider<GraphQlSourceBuilderCustomizer> sourceCustomizers,
            ReloadSchemaIndicator reloadSchemaIndicator,
            DataFetcherExceptionHandler defaultExceptionHandler,
            ObjectProvider<Consumer<SchemaReport>> reportConsumerProvider) {
        List<DataFetcherExceptionResolver> dataFetcherExceptionResolvers =
                new ArrayList<>(exceptionResolvers.orderedStream().toList());
        dataFetcherExceptionResolvers.add(new ExceptionHandlerResolverAdapter(defaultExceptionHandler));

        DgsGraphQLSourceBuilder builder = new DgsGraphQLSourceBuilder(
                dgsSchemaProvider, configProps.getIntrospection().isShowSdlComments());
        builder.exceptionResolvers(dataFetcherExceptionResolvers)
                .subscriptionExceptionResolvers(
                        subscriptionExceptionResolvers.orderedStream().toList())
                .instrumentation(instrumentations.orderedStream().toList());

        Consumer<SchemaReport> reportConsumer = reportConsumerProvider.getIfAvailable();
        if (properties.getSchema().getInspection().isEnabled()) {
            if (reportConsumer != null) {
                builder.inspectSchemaMappings(reportConsumer);
            } else if (LOG.isInfoEnabled()) {
                builder.inspectSchemaMappings(schemaReport -> {
                    StringBuilder messageBuilder = new StringBuilder("***Schema Report***\n");

                    List<String> arguments = schemaReport.unmappedArguments().entrySet().stream()
                            .map(entry -> {
                                if (entry.getKey() instanceof SelfDescribingDataFetcher<?> selfDescribing
                                        && selfDescribing
                                                instanceof DgsGraphQLSourceBuilder.DgsSelfDescribingDataFetcher
                                                        dgsDataFetcher) {
                                    var dataFetcher = dgsDataFetcher.getDataFetcher();
                                    return dataFetcher
                                                    .getMethod()
                                                    .getDeclaringClass()
                                                    .getName() + "."
                                            + dataFetcher.getMethod().getName() + " for arguments "
                                            + entry.getValue();
                                }
                                return entry.toString();
                            })
                            .toList();

                    messageBuilder
                            .append("Unmapped fields: ")
                            .append(schemaReport.unmappedFields())
                            .append('\n');
                    messageBuilder
                            .append("Unmapped registrations: ")
                            .append(schemaReport.unmappedRegistrations())
                            .append('\n');
                    messageBuilder.append("Unmapped arguments: ").append(arguments).append('\n');
                    messageBuilder
                            .append("Skipped types: ")
                            .append(schemaReport.skippedTypes())
                            .append('\n');

                    LOG.info("{}", messageBuilder);
                });
            }
        }

        wiringConfigurers.orderedStream().forEach(builder::configureRuntimeWiring);
        sourceCustomizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return new ReloadableGraphQLSource(builder, reloadSchemaIndicator);
    }

    @NotNull
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "reactor.core.publisher.Mono")
    public MonoDataFetcherResultProcessor monoReactiveDataFetcherResultProcessor() {
        return new MonoDataFetcherResultProcessor();
    }

    @NotNull
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "kotlinx.coroutines.flow.Flow")
    public FlowDataFetcherResultProcessor flowReactiveDataFetcherResultProcessor() {
        return new FlowDataFetcherResultProcessor();
    }

    @NotNull
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "reactor.core.publisher.Flux")
    public FluxDataFetcherResultProcessor fluxReactiveDataFetcherResultProcessor() {
        return new FluxDataFetcherResultProcessor();
    }

    /**
     * JDK 21+ only - Creates the dgsAsyncTaskExecutor which is used to run data fetchers automatically wrapped in
     * CompletableFuture. Can be provided by other frameworks to enable context propagation.
     */
    @NotNull
    @Bean
    @Qualifier("dgsAsyncTaskExecutor")
    @ConditionalOnJava(JavaVersion.TWENTY_ONE)
    @ConditionalOnMissingBean(name = "dgsAsyncTaskExecutor")
    @ConditionalOnProperty(
            prefix = AUTO_CONF_PREFIX + ".virtualthreads",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false)
    public AsyncTaskExecutor virtualThreadsTaskExecutor() {
        LOG.info("Enabling virtual threads for DGS");

        ContextRegistry contextRegistry = new ContextRegistry()
                .loadContextAccessors()
                .loadThreadLocalAccessors()
                .registerThreadLocalAccessor(new Slf4jThreadLocalAccessor());

        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("dgs-virtual-thread-");
        executor.setVirtualThreads(true);
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator(
                ContextSnapshotFactory.builder().contextRegistry(contextRegistry).build()));
        return executor;
    }

    /**
     * Default CoroutineDispatcher used for executing Kotlin suspend functions in data fetchers.
     * Defaults to {@code Dispatchers.Unconfined} which runs coroutines immediately on the calling thread.
     * Override this bean to customize the dispatcher for your specific use case.
     */
    @NotNull
    @Bean(defaultCandidate = false)
    @Qualifier("dgsCoroutineDispatcher")
    @ConditionalOnMissingBean(name = "dgsCoroutineDispatcher")
    public CoroutineDispatcher dgsCoroutineDispatcher() {
        return Dispatchers.getUnconfined();
    }

    @NotNull
    @Bean
    public MethodDataFetcherFactory methodDataFetcherFactory(
            @NotNull ObjectProvider<ArgumentResolver> argumentResolvers,
            @NotNull @Qualifier("dgsAsyncTaskExecutor") Optional<AsyncTaskExecutor> taskExecutorOptional,
            @NotNull @Qualifier("dgsCoroutineDispatcher") CoroutineDispatcher coroutineDispatcher) {
        AsyncTaskExecutor taskExecutor = taskExecutorOptional.orElse(null);

        return new MethodDataFetcherFactory(
                argumentResolvers.orderedStream().toList(),
                new DefaultParameterNameDiscoverer(),
                taskExecutor,
                coroutineDispatcher);
    }

    /**
     * {@link DgsQueryExecutorRequestCustomizer} implementation which copies headers into the request if the request is
     * a {@link MockHttpServletRequest}; intended to support test use cases.
     */
    @NotNull
    @Bean
    @ConditionalOnClass(name = "org.springframework.mock.web.MockHttpServletRequest")
    public DgsQueryExecutorRequestCustomizer mockRequestHeaderCustomizer() {
        return new DgsQueryExecutorRequestCustomizer() {
            @Override
            public WebRequest apply(WebRequest request, HttpHeaders headers) {
                if (headers == null || headers.isEmpty() || !(request instanceof NativeWebRequest nativeWebRequest)) {
                    return request;
                }
                if (!(nativeWebRequest.getNativeRequest() instanceof MockHttpServletRequest mockRequest)) {
                    return request;
                }
                headers.forEach((key, value) -> {
                    if (mockRequest.getHeader(key) == null) {
                        mockRequest.addHeader(key, value);
                    }
                });
                return request;
            }

            @Override
            public String toString() {
                return "{MockRequestHeaderCustomizer}";
            }
        };
    }

    @NotNull
    @Bean
    @DgsComponent
    public DgsRuntimeWiringConfigurerBridge dgsRuntimeWiringConfigurerBridge(
            @NotNull List<? extends RuntimeWiringConfigurer> configurers) {
        return new DgsRuntimeWiringConfigurerBridge(configurers);
    }

    public static class DgsRuntimeWiringConfigurerBridge {
        private final List<? extends RuntimeWiringConfigurer> configurers;

        public DgsRuntimeWiringConfigurerBridge(@NotNull List<? extends RuntimeWiringConfigurer> configurers) {
            this.configurers = configurers;
        }

        @NotNull
        @DgsRuntimeWiring
        public RuntimeWiring.Builder runtimeWiring(@NotNull RuntimeWiring.Builder builder) {
            configurers.forEach(configurer -> configurer.configure(builder));
            return builder;
        }
    }

    @NotNull
    @Bean
    @ConditionalOnProperty(name = "dgs.springgraphql.pagination.enabled", havingValue = "true", matchIfMissing = true)
    @DgsComponent
    public DgsTypeDefinitionConfigurerBridge dgsTypeDefinitionConfigurerBridge(@NotNull Environment environment) {
        return new DgsTypeDefinitionConfigurerBridge();
    }

    public static class DgsTypeDefinitionConfigurerBridge {
        @NotNull
        @DgsTypeDefinitionRegistry
        public TypeDefinitionRegistry typeDefinitionRegistry(@NotNull TypeDefinitionRegistry typeDefinitionRegistry) {
            TypeDefinitionRegistry newTypeDefinitionRegistry = new TypeDefinitionRegistry();
            new ConnectionTypeDefinitionConfigurer().configure(typeDefinitionRegistry);
            return newTypeDefinitionRegistry;
        }
    }

    @NotNull
    @Bean
    public GraphQlSourceBuilderCustomizer sourceBuilderCustomizer(
            @NotNull Optional<PreparsedDocumentProvider> preparsedDocumentProvider,
            @NotNull @Qualifier("query") Optional<ExecutionStrategy> providedQueryExecutionStrategy,
            @NotNull @Qualifier("mutation") Optional<ExecutionStrategy> providedMutationExecutionStrategy,
            @NotNull DataFetcherExceptionHandler dataFetcherExceptionHandler,
            @NotNull Environment environment) {
        return builder -> builder.configureGraphQl(graphQlBuilder -> {
            boolean apqEnabled = environment.getProperty("dgs.graphql.apq.enabled", Boolean.class, false);
            // If apq is enabled, we will not use this preparsedDocumentProvider and use
            // DgsAPQPreparsedDocumentProviderWrapper instead
            if (preparsedDocumentProvider.isPresent() && !apqEnabled) {
                graphQlBuilder.preparsedDocumentProvider(preparsedDocumentProvider.get());
            }

            if (providedQueryExecutionStrategy.isPresent()) {
                graphQlBuilder.queryExecutionStrategy(providedQueryExecutionStrategy.get());
            }

            if (providedMutationExecutionStrategy.isPresent()) {
                graphQlBuilder.mutationExecutionStrategy(providedMutationExecutionStrategy.get());
            }
        });
    }

    @NotNull
    @Bean
    @ConditionalOnProperty(
            name = "spring.graphql.schema.introspection.enabled",
            havingValue = "false",
            matchIfMissing = false)
    public GraphQLContextContributor disableIntrospectionContextContributor() {
        return (builder, extensions, requestData) -> builder.put(Introspection.INTROSPECTION_DISABLED, true);
    }

    @NotNull
    @Bean
    public DgsQueryExecutor springGraphQLDgsQueryExecutor(
            @NotNull ExecutionGraphQlService executionService,
            @NotNull DefaultDgsGraphQLContextBuilder dgsContextBuilder,
            @NotNull DgsDataLoaderProvider dgsDataLoaderProvider,
            @NotNull DgsJsonMapper dgsJsonMapper,
            @NotNull ObjectProvider<DgsQueryExecutorRequestCustomizer> requestCustomizer,
            @NotNull List<? extends GraphQLContextContributor> graphQLContextContributors) {
        return new SpringGraphQLDgsQueryExecutor(
                executionService,
                dgsContextBuilder,
                dgsDataLoaderProvider,
                dgsJsonMapper,
                requestCustomizer.getIfAvailable(() -> DgsQueryExecutorRequestCustomizer.DEFAULT_REQUEST_CUSTOMIZER),
                graphQLContextContributors);
    }

    /**
     * Backward compatibility for setting response headers through a "dgs-response-headers" field in extensions, or
     * using DgsExecutionResult. While this can easily be done through a custom WebGraphQlInterceptor, this bean
     * provides backward compatibility with older code.
     */
    @NotNull
    @Bean
    @ConditionalOnProperty(
            prefix = AUTO_CONF_PREFIX + ".dgs-response-headers",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public WebGraphQlInterceptor dgsHeadersInterceptor() {
        return (request, chain) -> chain.next(request).doOnNext(response -> {
            Object responseHeadersExtension = response.getExtensions().get("dgs-response-headers");
            if (responseHeadersExtension instanceof HttpHeaders httpHeaders) {
                response.getResponseHeaders().addAll(httpHeaders);
            }
            if (response.getExecutionResult() instanceof DgsExecutionResult dgsExecutionResult) {
                response.getResponseHeaders().addAll(dgsExecutionResult.getHeaders());
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public static class WebMvcConfiguration {
        private final DgsSpringGraphQLConfigurationProperties dgsSpringGraphQLConfigurationProperties;

        public WebMvcConfiguration(@NotNull DgsSpringGraphQLConfigurationProperties dgsSpringGraphQLConfigurationProperties) {
            this.dgsSpringGraphQLConfigurationProperties = dgsSpringGraphQLConfigurationProperties;
        }

        @NotNull
        @Bean
        public DgsWebMvcGraphQLInterceptor dgsGraphQlInterceptor(
                @NotNull DgsDataLoaderProvider dgsDataLoaderProvider,
                @NotNull DefaultDgsGraphQLContextBuilder dgsDefaultContextBuilder,
                @NotNull List<? extends GraphQLContextContributor> graphQLContextContributors) {
            return new DgsWebMvcGraphQLInterceptor(
                    dgsDataLoaderProvider,
                    dgsDefaultContextBuilder,
                    dgsSpringGraphQLConfigurationProperties,
                    graphQLContextContributors);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public static class WebMvcArgumentHandlerConfiguration {
        @Qualifier
        @Retention(RetentionPolicy.RUNTIME)
        private @interface Dgs {
        }

        @NotNull
        @Bean
        @Dgs
        public WebDataBinderFactory dgsWebDataBinderFactory(
                @NotNull @Qualifier("requestMappingHandlerAdapter") ObjectProvider<RequestMappingHandlerAdapter> adapter) {
            RequestMappingHandlerAdapter handlerAdapter = adapter.getIfAvailable();
            return new ServletRequestDataBinderFactory(
                    List.of(), handlerAdapter != null ? handlerAdapter.getWebBindingInitializer() : null);
        }

        @NotNull
        @Bean
        public ArgumentResolver requestHeaderMapResolver(@NotNull @Dgs WebDataBinderFactory dataBinderFactory) {
            return new HandlerMethodArgumentResolverAdapter(
                    new RequestHeaderMapMethodArgumentResolver(), dataBinderFactory);
        }

        @NotNull
        @Bean
        public ArgumentResolver requestHeaderResolver(
                @NotNull ConfigurableBeanFactory beanFactory, @NotNull @Dgs WebDataBinderFactory dataBinderFactory) {
            return new HandlerMethodArgumentResolverAdapter(
                    new RequestHeaderMethodArgumentResolver(beanFactory), dataBinderFactory);
        }

        @NotNull
        @Bean
        public ArgumentResolver requestParamResolver(@NotNull @Dgs WebDataBinderFactory dataBinderFactory) {
            return new HandlerMethodArgumentResolverAdapter(
                    new RequestParamMethodArgumentResolver(false), dataBinderFactory);
        }

        @NotNull
        @Bean
        public ArgumentResolver requestParamMapResolver(@NotNull @Dgs WebDataBinderFactory dataBinderFactory) {
            return new HandlerMethodArgumentResolverAdapter(
                    new RequestParamMapMethodArgumentResolver(), dataBinderFactory);
        }

        @NotNull
        @Bean
        public ArgumentResolver cookieValueResolver(
                @NotNull ConfigurableBeanFactory beanFactory, @NotNull @Dgs WebDataBinderFactory dataBinderFactory) {
            return new HandlerMethodArgumentResolverAdapter(
                    new ServletCookieValueMethodArgumentResolver(beanFactory), dataBinderFactory);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({Publisher.class, ServerRequest.class})
    public static class ReactiveConfiguration {
        @NotNull
        @Bean
        public DgsReactiveQueryExecutor springGraphQLDgsReactiveQueryExecutor(
                @NotNull ExecutionGraphQlService executionService,
                @NotNull DefaultDgsReactiveGraphQLContextBuilder dgsContextBuilder,
                @NotNull DgsDataLoaderProvider dgsDataLoaderProvider,
                @NotNull DgsJsonMapper dgsJsonMapper) {
            return new SpringGraphQLDgsReactiveQueryExecutor(
                    executionService, dgsContextBuilder, dgsDataLoaderProvider, dgsJsonMapper);
        }

        @NotNull
        @Bean
        @ConditionalOnMissingBean
        public DefaultDgsReactiveGraphQLContextBuilder reactiveGraphQlContextBuilder(
                @NotNull Optional<DgsReactiveCustomContextBuilderWithRequest<?>> dgsReactiveCustomContextBuilderWithRequest) {
            return new DefaultDgsReactiveGraphQLContextBuilder(dgsReactiveCustomContextBuilderWithRequest);
        }

        @NotNull
        @Bean
        @ConditionalOnMissingBean
        public ServerWebExchangeContextFilter dgsServerWebExchangeContextFilter() {
            return new ServerWebExchangeContextFilter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public static class WebFluxConfiguration {
        @NotNull
        @Bean
        public DgsWebFluxGraphQLInterceptor webFluxDgsGraphQLInterceptor(
                @NotNull DgsDataLoaderProvider dgsDataLoaderProvider,
                @NotNull DefaultDgsReactiveGraphQLContextBuilder defaultDgsReactiveGraphQLContextBuilder) {
            return new DgsWebFluxGraphQLInterceptor(dgsDataLoaderProvider, defaultDgsReactiveGraphQLContextBuilder);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public static class WebFluxArgumentHandlerConfiguration {
        @Qualifier
        @Retention(RetentionPolicy.RUNTIME)
        private @interface Dgs {
        }

        @NotNull
        @Dgs
        @Bean
        public BindingContext dgsBindingContext(
                @NotNull ObjectProvider<org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter>
                                adapter) {
            var handlerAdapter = adapter.getIfAvailable();
            return new BindingContext(handlerAdapter != null ? handlerAdapter.getWebBindingInitializer() : null);
        }

        @NotNull
        @Bean
        public ArgumentResolver cookieValueArgumentResolver(
                @NotNull ConfigurableBeanFactory beanFactory, @NotNull ReactiveAdapterRegistry registry, @NotNull @Dgs BindingContext bindingContext) {
            return new SyncHandlerMethodArgumentResolverAdapter(
                    new CookieValueMethodArgumentResolver(beanFactory, registry), bindingContext);
        }

        @NotNull
        @Bean
        public ArgumentResolver requestHeaderMapArgumentResolver(
                @NotNull ReactiveAdapterRegistry registry, @NotNull @Dgs BindingContext bindingContext) {
            return new SyncHandlerMethodArgumentResolverAdapter(
                    new org.springframework.web.reactive.result.method.annotation
                            .RequestHeaderMapMethodArgumentResolver(registry),
                    bindingContext);
        }

        @NotNull
        @Bean
        public ArgumentResolver requestHeaderArgumentResolver(
                @NotNull ConfigurableBeanFactory beanFactory, @NotNull ReactiveAdapterRegistry registry, @NotNull @Dgs BindingContext bindingContext) {
            return new SyncHandlerMethodArgumentResolverAdapter(
                    new org.springframework.web.reactive.result.method.annotation.RequestHeaderMethodArgumentResolver(
                            beanFactory, registry),
                    bindingContext);
        }

        @NotNull
        @Bean
        public ArgumentResolver requestParamArgumentResolver(
                @NotNull ConfigurableBeanFactory beanFactory, @NotNull ReactiveAdapterRegistry registry, @NotNull @Dgs BindingContext bindingContext) {
            return new SyncHandlerMethodArgumentResolverAdapter(
                    new org.springframework.web.reactive.result.method.annotation.RequestParamMethodArgumentResolver(
                            beanFactory, registry, false),
                    bindingContext);
        }

        @Bean
        public ArgumentResolver requestParamMapArgumentResolver(
                ReactiveAdapterRegistry registry, @Dgs BindingContext bindingContext) {
            return new SyncHandlerMethodArgumentResolverAdapter(
                    new org.springframework.web.reactive.result.method.annotation
                            .RequestParamMapMethodArgumentResolver(registry),
                    bindingContext);
        }
    }
}
