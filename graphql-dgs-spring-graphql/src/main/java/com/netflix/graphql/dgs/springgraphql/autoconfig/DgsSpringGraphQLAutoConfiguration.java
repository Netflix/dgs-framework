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
    public static final String AUTO_CONF_PREFIX = "dgs.graphql";

    private static final Logger LOG = LoggerFactory.getLogger(DgsSpringGraphQLAutoConfiguration.class);

    private final DgsConfigurationProperties configProps;
    private final DgsDataloaderConfigurationProperties dataloaderConfigProps;

    public DgsSpringGraphQLAutoConfiguration(
            DgsConfigurationProperties configProps, DgsDataloaderConfigurationProperties dataloaderConfigProps) {
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
        @Bean
        @ConditionalOnMissingBean(DgsJsonMapper.class)
        public DgsJsonMapper dgsJsonMapper() {
            return new Jackson3DgsJsonMapper();
        }
    }

    @Bean
    @ConditionalOnMissingBean(DgsJsonMapper.class)
    public DgsJsonMapper dgsJsonMapperFallback() {
        throw new DgsJsonMapperMissingException();
    }

    @Bean
    @Order(PriorityOrdered.HIGHEST_PRECEDENCE)
    public Instrumentation graphQLContextContributionInstrumentation(
            ObjectProvider<GraphQLContextContributor> graphQLContextContributors) {
        return new GraphQLContextContributorInstrumentation(
                graphQLContextContributors.orderedStream().toList());
    }

    // This instrumentation needs to run before MetricsInstrumentation
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

    @Bean
    @ConditionalOnMissingBean
    public QueryValueCustomizer defaultQueryValueCustomizer() {
        return query -> query;
    }

    @Bean
    @ConditionalOnMissingBean
    public DgsDataLoaderOptionsProvider dgsDataLoaderOptionsProvider() {
        return new DefaultDataLoaderOptionsProvider();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "dgsScheduledExecutorService")
    @Qualifier("dgsScheduledExecutorService")
    public ScheduledExecutorService dgsScheduledExecutorService() {
        return Executors.newSingleThreadScheduledExecutor();
    }

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

    @Bean
    @Order(100)
    public DgsDataLoaderInstrumentationDataLoaderCustomizer dgsDataLoaderInstrumentationDataLoaderCustomizer(
            List<DgsDataLoaderInstrumentation> instrumentations) {
        return new DgsDataLoaderInstrumentationDataLoaderCustomizer(instrumentations);
    }

    @Bean
    public DefaultDgsDataLoaderProvider dgsDataLoaderProvider(
            ApplicationContext applicationContext,
            DgsDataLoaderOptionsProvider dataloaderOptionProvider,
            @Qualifier("dgsScheduledExecutorService") ScheduledExecutorService dgsScheduledExecutorService,
            List<DataLoaderInstrumentationExtensionProvider> extensionProviders,
            List<DgsDataLoaderCustomizer> customizers) {
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

        public DgsDataLoaderReloadAutoConfiguration(DgsDataloaderConfigurationProperties dataloaderConfigProps) {
            this.dataloaderConfigProps = dataloaderConfigProps;
        }

        /**
         * Creates a {@link ReloadableDgsDataLoaderProvider} that wraps the standard {@link DgsDataLoaderProvider}.
         *
         * <p>The {@code @Primary} annotation ensures this bean takes precedence over the standard
         * {@code DgsDataLoaderProvider} when reload functionality is enabled.
         */
        @Bean
        @Primary
        public ReloadableDgsDataLoaderProvider reloadableDgsDataLoaderProvider(
                ApplicationContext applicationContext,
                DgsDataLoaderOptionsProvider dataLoaderOptionProvider,
                @Qualifier("dgsScheduledExecutorService") ScheduledExecutorService dgsScheduledExecutorService,
                List<DataLoaderInstrumentationExtensionProvider> extensionProviders,
                List<DgsDataLoaderCustomizer> customizers) {
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
        @Bean
        @ConditionalOnMissingBean
        public DgsDataLoaderReloadController dgsDataLoaderReloadController(
                ReloadableDgsDataLoaderProvider reloadableDgsDataLoaderProvider) {
            LOG.info("Creating data loader reload controller");
            return new DefaultDgsDataLoaderReloadController(reloadableDgsDataLoaderProvider);
        }
    }

    @Bean
    public EntityFetcherRegistry entityFetcherRegistry() {
        return new EntityFetcherRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public DataFetcherExceptionHandler dataFetcherExceptionHandler() {
        return new DefaultDataFetcherExceptionHandler();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = AUTO_CONF_PREFIX + ".preparsedDocumentProvider",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false)
    @ConditionalOnMissingBean
    public PreparsedDocumentProvider preparsedDocumentProvider(DgsConfigurationProperties configProps) {
        return new DgsDefaultPreparsedDocumentProvider(
                configProps.getPreparsedDocumentProvider().getMaximumCacheSize(),
                Duration.parse(configProps.getPreparsedDocumentProvider().getCacheValidityDuration()));
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultDgsGraphQLContextBuilder graphQLContextBuilder(
            Optional<DgsCustomContextBuilder<?>> dgsCustomContextBuilder,
            Optional<DgsCustomContextBuilderWithRequest<?>> dgsCustomContextBuilderWithRequest) {
        return new DefaultDgsGraphQLContextBuilder(dgsCustomContextBuilder, dgsCustomContextBuilderWithRequest);
    }

    /**
     * Used by the {@link ReloadableGraphQLSource}, it controls if, and when, such executor should reload the schema.
     * This implementation will return either the boolean value of the {@code dgs.reload} flag
     * or {@code true} if the {@code laptop} profile is an active Spring Boot profile.
     */
    @Bean
    @ConditionalOnMissingBean
    public ReloadSchemaIndicator defaultReloadSchemaIndicator(Environment environment) {
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
            List<DataFetcherResultProcessor> dataFetcherResultProcessors,
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

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "reactor.core.publisher.Mono")
    public MonoDataFetcherResultProcessor monoReactiveDataFetcherResultProcessor() {
        return new MonoDataFetcherResultProcessor();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "kotlinx.coroutines.flow.Flow")
    public FlowDataFetcherResultProcessor flowReactiveDataFetcherResultProcessor() {
        return new FlowDataFetcherResultProcessor();
    }

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
    @Bean(defaultCandidate = false)
    @Qualifier("dgsCoroutineDispatcher")
    @ConditionalOnMissingBean(name = "dgsCoroutineDispatcher")
    public CoroutineDispatcher dgsCoroutineDispatcher() {
        return Dispatchers.getUnconfined();
    }

    @Bean
    public MethodDataFetcherFactory methodDataFetcherFactory(
            ObjectProvider<ArgumentResolver> argumentResolvers,
            @Qualifier("dgsAsyncTaskExecutor") Optional<AsyncTaskExecutor> taskExecutorOptional,
            @Qualifier("dgsCoroutineDispatcher") CoroutineDispatcher coroutineDispatcher) {
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

    @Bean
    @DgsComponent
    public DgsRuntimeWiringConfigurerBridge dgsRuntimeWiringConfigurerBridge(List<RuntimeWiringConfigurer> configurers) {
        return new DgsRuntimeWiringConfigurerBridge(configurers);
    }

    public static class DgsRuntimeWiringConfigurerBridge {
        private final List<RuntimeWiringConfigurer> configurers;

        public DgsRuntimeWiringConfigurerBridge(List<RuntimeWiringConfigurer> configurers) {
            this.configurers = configurers;
        }

        @DgsRuntimeWiring
        public RuntimeWiring.Builder runtimeWiring(RuntimeWiring.Builder builder) {
            configurers.forEach(configurer -> configurer.configure(builder));
            return builder;
        }
    }

    @Bean
    @ConditionalOnProperty(name = "dgs.springgraphql.pagination.enabled", havingValue = "true", matchIfMissing = true)
    @DgsComponent
    public DgsTypeDefinitionConfigurerBridge dgsTypeDefinitionConfigurerBridge(Environment environment) {
        return new DgsTypeDefinitionConfigurerBridge();
    }

    public static class DgsTypeDefinitionConfigurerBridge {
        @DgsTypeDefinitionRegistry
        public TypeDefinitionRegistry typeDefinitionRegistry(TypeDefinitionRegistry typeDefinitionRegistry) {
            TypeDefinitionRegistry newTypeDefinitionRegistry = new TypeDefinitionRegistry();
            new ConnectionTypeDefinitionConfigurer().configure(typeDefinitionRegistry);
            return newTypeDefinitionRegistry;
        }
    }

    @Bean
    public GraphQlSourceBuilderCustomizer sourceBuilderCustomizer(
            Optional<PreparsedDocumentProvider> preparsedDocumentProvider,
            @Qualifier("query") Optional<ExecutionStrategy> providedQueryExecutionStrategy,
            @Qualifier("mutation") Optional<ExecutionStrategy> providedMutationExecutionStrategy,
            DataFetcherExceptionHandler dataFetcherExceptionHandler,
            Environment environment) {
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

    @Bean
    @ConditionalOnProperty(
            name = "spring.graphql.schema.introspection.enabled",
            havingValue = "false",
            matchIfMissing = false)
    public GraphQLContextContributor disableIntrospectionContextContributor() {
        return (builder, extensions, requestData) -> builder.put(Introspection.INTROSPECTION_DISABLED, true);
    }

    @Bean
    public DgsQueryExecutor springGraphQLDgsQueryExecutor(
            ExecutionGraphQlService executionService,
            DefaultDgsGraphQLContextBuilder dgsContextBuilder,
            DgsDataLoaderProvider dgsDataLoaderProvider,
            DgsJsonMapper dgsJsonMapper,
            ObjectProvider<DgsQueryExecutorRequestCustomizer> requestCustomizer,
            List<GraphQLContextContributor> graphQLContextContributors) {
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

        public WebMvcConfiguration(DgsSpringGraphQLConfigurationProperties dgsSpringGraphQLConfigurationProperties) {
            this.dgsSpringGraphQLConfigurationProperties = dgsSpringGraphQLConfigurationProperties;
        }

        @Bean
        public DgsWebMvcGraphQLInterceptor dgsGraphQlInterceptor(
                DgsDataLoaderProvider dgsDataLoaderProvider,
                DefaultDgsGraphQLContextBuilder dgsDefaultContextBuilder,
                List<GraphQLContextContributor> graphQLContextContributors) {
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

        @Bean
        @Dgs
        public WebDataBinderFactory dgsWebDataBinderFactory(
                @Qualifier("requestMappingHandlerAdapter") ObjectProvider<RequestMappingHandlerAdapter> adapter) {
            RequestMappingHandlerAdapter handlerAdapter = adapter.getIfAvailable();
            return new ServletRequestDataBinderFactory(
                    List.of(), handlerAdapter != null ? handlerAdapter.getWebBindingInitializer() : null);
        }

        @Bean
        public ArgumentResolver requestHeaderMapResolver(@Dgs WebDataBinderFactory dataBinderFactory) {
            return new HandlerMethodArgumentResolverAdapter(
                    new RequestHeaderMapMethodArgumentResolver(), dataBinderFactory);
        }

        @Bean
        public ArgumentResolver requestHeaderResolver(
                ConfigurableBeanFactory beanFactory, @Dgs WebDataBinderFactory dataBinderFactory) {
            return new HandlerMethodArgumentResolverAdapter(
                    new RequestHeaderMethodArgumentResolver(beanFactory), dataBinderFactory);
        }

        @Bean
        public ArgumentResolver requestParamResolver(@Dgs WebDataBinderFactory dataBinderFactory) {
            return new HandlerMethodArgumentResolverAdapter(
                    new RequestParamMethodArgumentResolver(false), dataBinderFactory);
        }

        @Bean
        public ArgumentResolver requestParamMapResolver(@Dgs WebDataBinderFactory dataBinderFactory) {
            return new HandlerMethodArgumentResolverAdapter(
                    new RequestParamMapMethodArgumentResolver(), dataBinderFactory);
        }

        @Bean
        public ArgumentResolver cookieValueResolver(
                ConfigurableBeanFactory beanFactory, @Dgs WebDataBinderFactory dataBinderFactory) {
            return new HandlerMethodArgumentResolverAdapter(
                    new ServletCookieValueMethodArgumentResolver(beanFactory), dataBinderFactory);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({Publisher.class, ServerRequest.class})
    public static class ReactiveConfiguration {
        @Bean
        public DgsReactiveQueryExecutor springGraphQLDgsReactiveQueryExecutor(
                ExecutionGraphQlService executionService,
                DefaultDgsReactiveGraphQLContextBuilder dgsContextBuilder,
                DgsDataLoaderProvider dgsDataLoaderProvider,
                DgsJsonMapper dgsJsonMapper) {
            return new SpringGraphQLDgsReactiveQueryExecutor(
                    executionService, dgsContextBuilder, dgsDataLoaderProvider, dgsJsonMapper);
        }

        @Bean
        @ConditionalOnMissingBean
        public DefaultDgsReactiveGraphQLContextBuilder reactiveGraphQlContextBuilder(
                Optional<DgsReactiveCustomContextBuilderWithRequest<?>> dgsReactiveCustomContextBuilderWithRequest) {
            return new DefaultDgsReactiveGraphQLContextBuilder(dgsReactiveCustomContextBuilderWithRequest);
        }

        @Bean
        @ConditionalOnMissingBean
        public ServerWebExchangeContextFilter dgsServerWebExchangeContextFilter() {
            return new ServerWebExchangeContextFilter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public static class WebFluxConfiguration {
        @Bean
        public DgsWebFluxGraphQLInterceptor webFluxDgsGraphQLInterceptor(
                DgsDataLoaderProvider dgsDataLoaderProvider,
                DefaultDgsReactiveGraphQLContextBuilder defaultDgsReactiveGraphQLContextBuilder) {
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

        @Dgs
        @Bean
        public BindingContext dgsBindingContext(
                ObjectProvider<org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter>
                                adapter) {
            var handlerAdapter = adapter.getIfAvailable();
            return new BindingContext(handlerAdapter != null ? handlerAdapter.getWebBindingInitializer() : null);
        }

        @Bean
        public ArgumentResolver cookieValueArgumentResolver(
                ConfigurableBeanFactory beanFactory, ReactiveAdapterRegistry registry, @Dgs BindingContext bindingContext) {
            return new SyncHandlerMethodArgumentResolverAdapter(
                    new CookieValueMethodArgumentResolver(beanFactory, registry), bindingContext);
        }

        @Bean
        public ArgumentResolver requestHeaderMapArgumentResolver(
                ReactiveAdapterRegistry registry, @Dgs BindingContext bindingContext) {
            return new SyncHandlerMethodArgumentResolverAdapter(
                    new org.springframework.web.reactive.result.method.annotation
                            .RequestHeaderMapMethodArgumentResolver(registry),
                    bindingContext);
        }

        @Bean
        public ArgumentResolver requestHeaderArgumentResolver(
                ConfigurableBeanFactory beanFactory, ReactiveAdapterRegistry registry, @Dgs BindingContext bindingContext) {
            return new SyncHandlerMethodArgumentResolverAdapter(
                    new org.springframework.web.reactive.result.method.annotation.RequestHeaderMethodArgumentResolver(
                            beanFactory, registry),
                    bindingContext);
        }

        @Bean
        public ArgumentResolver requestParamArgumentResolver(
                ConfigurableBeanFactory beanFactory, ReactiveAdapterRegistry registry, @Dgs BindingContext bindingContext) {
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
