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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.DgsRuntimeWiring
import com.netflix.graphql.dgs.internal.*
import com.netflix.graphql.dgs.internal.method.ArgumentResolver
import com.netflix.graphql.dgs.mvc.internal.method.HandlerMethodArgumentResolverAdapter
import com.netflix.graphql.dgs.reactive.DgsReactiveCustomContextBuilderWithRequest
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveGraphQLContextBuilder
import com.netflix.graphql.dgs.reactive.internal.method.SyncHandlerMethodArgumentResolverAdapter
import com.netflix.graphql.dgs.springgraphql.DgsGraphQLInterceptor
import com.netflix.graphql.dgs.springgraphql.DgsGraphQLSourceBuilder
import com.netflix.graphql.dgs.springgraphql.SpringGraphQLDgsQueryExecutor
import com.netflix.graphql.dgs.springgraphql.SpringGraphQLDgsReactiveQueryExecutor
import com.netflix.graphql.dgs.springgraphql.WebFluxDgsGraphQLInterceptor
import graphql.execution.instrumentation.Instrumentation
import graphql.schema.idl.RuntimeWiring
import org.reactivestreams.Publisher
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.graphql.execution.ConnectionTypeDefinitionConfigurer
import org.springframework.graphql.execution.DataFetcherExceptionResolver
import org.springframework.graphql.execution.DefaultExecutionGraphQlService
import org.springframework.graphql.execution.GraphQlSource
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import org.springframework.graphql.execution.SubscriptionExceptionResolver
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter
import org.springframework.web.method.annotation.RequestHeaderMapMethodArgumentResolver
import org.springframework.web.method.annotation.RequestHeaderMethodArgumentResolver
import org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver
import org.springframework.web.reactive.BindingContext
import org.springframework.web.reactive.result.method.annotation.CookieValueMethodArgumentResolver
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter
import org.springframework.web.servlet.mvc.method.annotation.ServletCookieValueMethodArgumentResolver
import org.springframework.web.servlet.mvc.method.annotation.ServletRequestDataBinderFactory
import java.util.*

/**
 * Framework auto configuration based on open source Spring only, without Netflix integrations.
 * This does NOT have logging, tracing, metrics and security integration.
 */
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@AutoConfiguration
@AutoConfigureBefore(name = ["com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration"])
open class DgsSpringGraphQLAutoConfiguration {

    @Bean
    @DgsComponent
    open fun dgsRuntimeWiringConfigurerBridge(configurers: List<RuntimeWiringConfigurer>): DgsRuntimeWiringConfigurerBridge {
        return DgsRuntimeWiringConfigurerBridge(configurers)
    }

    class DgsRuntimeWiringConfigurerBridge(private val configurers: List<RuntimeWiringConfigurer>) {
        @DgsRuntimeWiring
        fun runtimeWiring(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
            configurers.forEach {
                it.configure(builder)
            }

            return builder
        }
    }

    @Bean
    open fun sourceBuilderCustomizer(): GraphQlSourceBuilderCustomizer {
        return GraphQlSourceBuilderCustomizer { }
    }

    @Bean
    open fun graphQlSource(
        dgsSchemaProvider: DgsSchemaProvider,
        exceptionResolvers: ObjectProvider<DataFetcherExceptionResolver>,
        subscriptionExceptionResolvers: ObjectProvider<SubscriptionExceptionResolver>,
        instrumentations: ObjectProvider<Instrumentation?>,
        wiringConfigurers: ObjectProvider<RuntimeWiringConfigurer>,
        sourceCustomizers: ObjectProvider<GraphQlSourceBuilderCustomizer>,
        reloadSchemaIndicator: DefaultDgsQueryExecutor.ReloadSchemaIndicator
    ): GraphQlSource {
        val builder = DgsGraphQLSourceBuilder(dgsSchemaProvider, reloadSchemaIndicator)
            .exceptionResolvers(exceptionResolvers.orderedStream().toList())
            .subscriptionExceptionResolvers(subscriptionExceptionResolvers.orderedStream().toList())
            .instrumentation(instrumentations.orderedStream().toList())

        builder.configureTypeDefinitions(ConnectionTypeDefinitionConfigurer())
        wiringConfigurers.orderedStream().forEach { configurer: RuntimeWiringConfigurer ->
            builder.configureRuntimeWiring(
                configurer
            )
        }
        sourceCustomizers.orderedStream().forEach { customizer: GraphQlSourceBuilderCustomizer ->
            customizer.customize(
                builder
            )
        }
        return builder.build()
    }

    @Bean
    open fun springGraphQLDgsQueryExecutor(
        executionService: DefaultExecutionGraphQlService,
        dgsContextBuilder: DefaultDgsGraphQLContextBuilder,
        dgsDataLoaderProvider: DgsDataLoaderProvider,
        requestCustomizer: ObjectProvider<DgsQueryExecutorRequestCustomizer>
    ): DgsQueryExecutor {
        return SpringGraphQLDgsQueryExecutor(
            executionService,
            dgsContextBuilder,
            dgsDataLoaderProvider,
            requestCustomizer = requestCustomizer.getIfAvailable(DgsQueryExecutorRequestCustomizer::DEFAULT_REQUEST_CUSTOMIZER)
        )
    }

    @Bean
    @Qualifier("dgsObjectMapper")
    @ConditionalOnMissingBean(name = ["dgsObjectMapper"])
    open fun dgsObjectMapper(): ObjectMapper {
        return jacksonObjectMapper().registerModule(JavaTimeModule())
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    open class WebMvcConfiguration {
        @Bean
        open fun dgsGraphQlInterceptor(
            dgsDataLoaderProvider: DgsDataLoaderProvider,
            dgsDefaultContextBuilder: DefaultDgsGraphQLContextBuilder
        ): DgsGraphQLInterceptor {
            return DgsGraphQLInterceptor(
                dgsDataLoaderProvider,
                dgsDefaultContextBuilder
            )
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    open class WebMvcArgumentHandlerConfiguration {

        @Qualifier
        private annotation class Dgs

        @Bean
        @Dgs
        open fun dgsWebDataBinderFactory(adapter: ObjectProvider<RequestMappingHandlerAdapter>): WebDataBinderFactory {
            return ServletRequestDataBinderFactory(listOf(), adapter.ifAvailable?.webBindingInitializer)
        }

        @Bean
        open fun requestHeaderMapResolver(@Dgs dataBinderFactory: WebDataBinderFactory): ArgumentResolver {
            return HandlerMethodArgumentResolverAdapter(RequestHeaderMapMethodArgumentResolver(), dataBinderFactory)
        }

        @Bean
        open fun requestHeaderResolver(
            beanFactory: ConfigurableBeanFactory,
            @Dgs dataBinderFactory: WebDataBinderFactory
        ): ArgumentResolver {
            return HandlerMethodArgumentResolverAdapter(
                RequestHeaderMethodArgumentResolver(beanFactory),
                dataBinderFactory
            )
        }

        @Bean
        open fun requestParamResolver(@Dgs dataBinderFactory: WebDataBinderFactory): ArgumentResolver {
            return HandlerMethodArgumentResolverAdapter(RequestParamMethodArgumentResolver(false), dataBinderFactory)
        }

        @Bean
        open fun requestParamMapResolver(@Dgs dataBinderFactory: WebDataBinderFactory): ArgumentResolver {
            return HandlerMethodArgumentResolverAdapter(RequestParamMapMethodArgumentResolver(), dataBinderFactory)
        }

        @Bean
        open fun cookieValueResolver(
            beanFactory: ConfigurableBeanFactory,
            @Dgs dataBinderFactory: WebDataBinderFactory
        ): ArgumentResolver {
            return HandlerMethodArgumentResolverAdapter(
                ServletCookieValueMethodArgumentResolver(beanFactory),
                dataBinderFactory
            )
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Publisher::class)
    open class ReactiveConfiguration {
        @Bean
        open fun springGraphQLDgsReactiveQueryExecutor(
            executionService: DefaultExecutionGraphQlService,
            dgsContextBuilder: DefaultDgsReactiveGraphQLContextBuilder,
            dgsDataLoaderProvider: DgsDataLoaderProvider
        ): DgsReactiveQueryExecutor {
            return SpringGraphQLDgsReactiveQueryExecutor(executionService, dgsContextBuilder, dgsDataLoaderProvider)
        }

        @Bean
        @ConditionalOnMissingBean
        open fun reactiveGraphQlContextBuilder(
            dgsReactiveCustomContextBuilderWithRequest: Optional<DgsReactiveCustomContextBuilderWithRequest<*>>
        ): DefaultDgsReactiveGraphQLContextBuilder {
            return DefaultDgsReactiveGraphQLContextBuilder(dgsReactiveCustomContextBuilderWithRequest)
        }

        @Bean
        open fun serverWebExchangeContextFilter(): ServerWebExchangeContextFilter {
            return ServerWebExchangeContextFilter()
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    open class WebFluxConfiguration {
        @Bean
        open fun webFluxDgsGraphQLInterceptor(
            dgsDataLoaderProvider: DgsDataLoaderProvider,
            defaultDgsReactiveGraphQLContextBuilder: DefaultDgsReactiveGraphQLContextBuilder
        ): WebFluxDgsGraphQLInterceptor {
            return WebFluxDgsGraphQLInterceptor(
                dgsDataLoaderProvider,
                defaultDgsReactiveGraphQLContextBuilder
            )
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    open class WebFluxArgumentHandlerConfiguration {
        @Qualifier
        private annotation class Dgs

        @Dgs
        @Bean
        open fun dgsBindingContext(adapter: ObjectProvider<org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter>): BindingContext {
            return BindingContext(adapter.ifAvailable?.webBindingInitializer)
        }

        @Bean
        open fun cookieValueArgumentResolver(
            beanFactory: ConfigurableBeanFactory,
            registry: ReactiveAdapterRegistry,
            @Dgs bindingContext: BindingContext
        ): ArgumentResolver {
            return SyncHandlerMethodArgumentResolverAdapter(
                CookieValueMethodArgumentResolver(beanFactory, registry),
                bindingContext
            )
        }

        @Bean
        open fun requestHeaderMapArgumentResolver(
            registry: ReactiveAdapterRegistry,
            @Dgs bindingContext: BindingContext
        ): ArgumentResolver {
            return SyncHandlerMethodArgumentResolverAdapter(
                org.springframework.web.reactive.result.method.annotation.RequestHeaderMapMethodArgumentResolver(
                    registry
                ),
                bindingContext
            )
        }

        @Bean
        open fun requestHeaderArgumentResolver(
            beanFactory: ConfigurableBeanFactory,
            registry: ReactiveAdapterRegistry,
            @Dgs bindingContext: BindingContext
        ): ArgumentResolver {
            return SyncHandlerMethodArgumentResolverAdapter(
                org.springframework.web.reactive.result.method.annotation.RequestHeaderMethodArgumentResolver(
                    beanFactory,
                    registry
                ),
                bindingContext
            )
        }

        @Bean
        open fun requestParamArgumentResolver(
            beanFactory: ConfigurableBeanFactory,
            registry: ReactiveAdapterRegistry,
            @Dgs bindingContext: BindingContext
        ): ArgumentResolver {
            return SyncHandlerMethodArgumentResolverAdapter(
                org.springframework.web.reactive.result.method.annotation.RequestParamMethodArgumentResolver(
                    beanFactory,
                    registry,
                    false
                ),
                bindingContext
            )
        }

        @Bean
        open fun requestParamMapArgumentResolver(
            beanFactory: ConfigurableBeanFactory,
            registry: ReactiveAdapterRegistry,
            @Dgs bindingContext: BindingContext
        ): ArgumentResolver {
            return SyncHandlerMethodArgumentResolverAdapter(
                org.springframework.web.reactive.result.method.annotation.RequestParamMapMethodArgumentResolver(registry),
                bindingContext
            )
        }
    }
}
