/*
 * Copyright 2024 Netflix, Inc.
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

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.DgsRuntimeWiring
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.context.GraphQLContextContributor
import com.netflix.graphql.dgs.internal.*
import com.netflix.graphql.dgs.internal.method.ArgumentResolver
import com.netflix.graphql.dgs.mvc.internal.method.HandlerMethodArgumentResolverAdapter
import com.netflix.graphql.dgs.reactive.DgsReactiveCustomContextBuilderWithRequest
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveGraphQLContextBuilder
import com.netflix.graphql.dgs.reactive.internal.method.SyncHandlerMethodArgumentResolverAdapter
import com.netflix.graphql.dgs.springgraphql.SpringGraphQLDgsQueryExecutor
import com.netflix.graphql.dgs.springgraphql.SpringGraphQLDgsReactiveQueryExecutor
import com.netflix.graphql.dgs.springgraphql.webflux.DgsWebFluxGraphQLInterceptor
import com.netflix.graphql.dgs.springgraphql.webmvc.DgsWebMvcGraphQLInterceptor
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.ExecutionStrategy
import graphql.execution.preparsed.PreparsedDocumentProvider
import graphql.introspection.Introspection
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeDefinitionRegistry
import org.reactivestreams.Publisher
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.core.env.Environment
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.execution.*
import org.springframework.web.bind.support.WebDataBinderFactory
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
import java.util.Optional

/**
 * Framework auto configuration based on open source Spring only, without Netflix integrations.
 * This does NOT have logging, tracing, metrics and security integration.
 */
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@AutoConfiguration
@AutoConfigureBefore(name = ["com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration"])
@EnableConfigurationProperties(DgsSpringGraphQLConfigurationProperties::class, DgsGraphQLConfigurationProperties::class)
open class DgsSpringGraphQLAutoConfiguration {
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
            ConnectionTypeDefinitionConfigurer().configure(typeDefinitionRegistry)
            return newTypeDefinitionRegistry
        }
    }

    @Bean
    open fun sourceBuilderCustomizer(
        preparsedDocumentProvider: Optional<PreparsedDocumentProvider>,
        @Qualifier("query") providedQueryExecutionStrategy: Optional<ExecutionStrategy>,
        @Qualifier("mutation") providedMutationExecutionStrategy: Optional<ExecutionStrategy>,
        dataFetcherExceptionHandler: DataFetcherExceptionHandler,
    ): GraphQlSourceBuilderCustomizer {
        val queryExecutionStrategy =
            providedQueryExecutionStrategy.orElse(AsyncExecutionStrategy(dataFetcherExceptionHandler))
        val mutationExecutionStrategy =
            providedMutationExecutionStrategy.orElse(AsyncSerialExecutionStrategy(dataFetcherExceptionHandler))

        return GraphQlSourceBuilderCustomizer { builder ->
            builder.configureGraphQl { graphQlBuilder ->
                if (preparsedDocumentProvider.isPresent) {
                    graphQlBuilder
                        .preparsedDocumentProvider(preparsedDocumentProvider.get())
                }
                graphQlBuilder
                    .queryExecutionStrategy(queryExecutionStrategy)
                    .mutationExecutionStrategy(mutationExecutionStrategy)
            }
        }
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "spring.graphql.schema.introspection",
        name = ["enabled"],
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
    ): DgsQueryExecutor =
        SpringGraphQLDgsQueryExecutor(
            executionService,
            dgsContextBuilder,
            dgsDataLoaderProvider,
            requestCustomizer = requestCustomizer.getIfAvailable(DgsQueryExecutorRequestCustomizer::DEFAULT_REQUEST_CUSTOMIZER),
        )

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    open class WebMvcConfiguration(
        private val dgsSpringGraphQLConfigurationProperties: DgsSpringGraphQLConfigurationProperties,
    ) {
        @Bean
        open fun dgsGraphQlInterceptor(
            dgsDataLoaderProvider: DgsDataLoaderProvider,
            dgsDefaultContextBuilder: DefaultDgsGraphQLContextBuilder,
        ): DgsWebMvcGraphQLInterceptor =
            DgsWebMvcGraphQLInterceptor(
                dgsDataLoaderProvider,
                dgsDefaultContextBuilder,
                dgsSpringGraphQLConfigurationProperties,
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
        open fun serverWebExchangeContextFilter(): ServerWebExchangeContextFilter = ServerWebExchangeContextFilter()
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
