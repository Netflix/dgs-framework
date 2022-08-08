/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.graphql.dgs.webflux.autoconfiguration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.internal.DefaultDgsQueryExecutor
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.FluxDataFetcherResultProcessor
import com.netflix.graphql.dgs.internal.MonoDataFetcherResultProcessor
import com.netflix.graphql.dgs.internal.QueryValueCustomizer
import com.netflix.graphql.dgs.internal.method.ArgumentResolver
import com.netflix.graphql.dgs.reactive.DgsReactiveCustomContextBuilderWithRequest
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveGraphQLContextBuilder
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveQueryExecutor
import com.netflix.graphql.dgs.reactive.internal.method.SyncHandlerMethodArgumentResolverAdapter
import com.netflix.graphql.dgs.webflux.handlers.DefaultDgsWebfluxHttpHandler
import com.netflix.graphql.dgs.webflux.handlers.DgsHandshakeWebSocketService
import com.netflix.graphql.dgs.webflux.handlers.DgsReactiveWebsocketHandler
import com.netflix.graphql.dgs.webflux.handlers.DgsWebfluxHttpHandler
import com.netflix.graphql.dgs.webflux.handlers.GraphQLMediaTypes
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.ExecutionIdProvider
import graphql.execution.ExecutionStrategy
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.preparsed.PreparsedDocumentProvider
import graphql.introspection.IntrospectionQuery
import graphql.schema.GraphQLSchema
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.core.env.Environment
import org.springframework.web.reactive.BindingContext
import org.springframework.web.reactive.function.server.RequestPredicates.accept
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.ServerResponse.permanentRedirect
import org.springframework.web.reactive.function.server.json
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.result.method.annotation.CookieValueMethodArgumentResolver
import org.springframework.web.reactive.result.method.annotation.RequestHeaderMapMethodArgumentResolver
import org.springframework.web.reactive.result.method.annotation.RequestHeaderMethodArgumentResolver
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter
import org.springframework.web.reactive.result.method.annotation.RequestParamMapMethodArgumentResolver
import org.springframework.web.reactive.result.method.annotation.RequestParamMethodArgumentResolver
import org.springframework.web.reactive.socket.server.WebSocketService
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy
import reactor.core.publisher.Mono
import reactor.netty.http.server.WebsocketServerSpec
import java.net.URI
import java.util.*
import kotlin.streams.toList

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
@EnableConfigurationProperties(DgsWebfluxConfigurationProperties::class)
open class DgsWebFluxAutoConfiguration(private val configProps: DgsWebfluxConfigurationProperties) {

    @Bean
    open fun dgsReactiveQueryExecutor(
        applicationContext: ApplicationContext,
        schema: GraphQLSchema,
        schemaProvider: DgsSchemaProvider,
        dgsDataLoaderProvider: DgsDataLoaderProvider,
        dgsContextBuilder: DefaultDgsReactiveGraphQLContextBuilder,
        dataFetcherExceptionHandler: DataFetcherExceptionHandler,
        instrumentations: ObjectProvider<Instrumentation>,
        environment: Environment,
        @Qualifier("query") providedQueryExecutionStrategy: Optional<ExecutionStrategy>,
        @Qualifier("mutation") providedMutationExecutionStrategy: Optional<ExecutionStrategy>,
        idProvider: Optional<ExecutionIdProvider>,
        reloadSchemaIndicator: DefaultDgsQueryExecutor.ReloadSchemaIndicator,
        preparsedDocumentProvider: ObjectProvider<PreparsedDocumentProvider>,
        queryValueCustomizer: QueryValueCustomizer
    ): DgsReactiveQueryExecutor {

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

        return DefaultDgsReactiveQueryExecutor(
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
            queryValueCustomizer = queryValueCustomizer
        )
    }

    @Bean
    @ConditionalOnMissingBean
    open fun reactiveGraphQlContextBuilder(
        dgsReactiveCustomContextBuilderWithRequest: Optional<DgsReactiveCustomContextBuilderWithRequest<*>>
    ): DefaultDgsReactiveGraphQLContextBuilder {
        return DefaultDgsReactiveGraphQLContextBuilder(dgsReactiveCustomContextBuilderWithRequest)
    }

    @Bean
    @ConditionalOnProperty(name = ["dgs.graphql.graphiql.enabled"], havingValue = "true", matchIfMissing = true)
    open fun graphiQlConfigurer(configProps: DgsWebfluxConfigurationProperties): GraphiQlConfigurer {
        return GraphiQlConfigurer(configProps)
    }

    @Bean
    @ConditionalOnProperty(name = ["dgs.graphql.graphiql.enabled"], havingValue = "true", matchIfMissing = true)
    open fun graphiQlIndexRedirect(): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            .GET(configProps.graphiql.path) {
                permanentRedirect(URI.create(configProps.graphiql.path + "/index.html")).build()
            }
            .build()
    }

    @Bean
    @Qualifier("dgsObjectMapper")
    @ConditionalOnMissingBean(name = ["dgsObjectMapper"])
    open fun dgsObjectMapper(): ObjectMapper {
        return jacksonObjectMapper()
    }

    @Bean
    @ConditionalOnMissingBean
    open fun dgsWebfluxHttpHandler(
        dgsQueryExecutor: DgsReactiveQueryExecutor,
        @Qualifier("dgsObjectMapper") dgsObjectMapper: ObjectMapper
    ): DgsWebfluxHttpHandler {
        return DefaultDgsWebfluxHttpHandler(dgsQueryExecutor, dgsObjectMapper)
    }

    @Bean
    open fun dgsGraphQlRouter(dgsWebfluxHttpHandler: DgsWebfluxHttpHandler): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            .POST(
                configProps.path,
                accept(*GraphQLMediaTypes.ACCEPTABLE_MEDIA_TYPES.toTypedArray()),
                dgsWebfluxHttpHandler::graphql
            ).build()
    }

    @Bean
    @ConditionalOnProperty(name = ["dgs.graphql.schema-json.enabled"], havingValue = "true", matchIfMissing = true)
    open fun schemaRouter(schemaProvider: DgsSchemaProvider): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            .GET(
                configProps.schemaJson.path
            ) {
                val graphQLSchema: GraphQLSchema = schemaProvider.schema()
                val graphQL = GraphQL.newGraphQL(graphQLSchema).build()

                val executionInput: ExecutionInput =
                    ExecutionInput.newExecutionInput().query(IntrospectionQuery.INTROSPECTION_QUERY)
                        .build()
                val execute = graphQL.executeAsync(executionInput)

                return@GET Mono.fromCompletionStage(execute)
                    .map { it.toSpecification() }
                    .flatMap { ok().json().bodyValue(it) }
            }.build()
    }

    @Bean
    open fun websocketSubscriptionHandler(dgsReactiveQueryExecutor: DgsReactiveQueryExecutor): SimpleUrlHandlerMapping {
        val simpleUrlHandlerMapping =
            SimpleUrlHandlerMapping(mapOf("/subscriptions" to DgsReactiveWebsocketHandler(dgsReactiveQueryExecutor)))
        simpleUrlHandlerMapping.order = 1
        return simpleUrlHandlerMapping
    }

    @Bean
    open fun webSocketService(): WebSocketService {
        val strategy = ReactorNettyRequestUpgradeStrategy { WebsocketServerSpec.builder().protocols("graphql-ws") }
        return DgsHandshakeWebSocketService(strategy)
    }

    @Bean
    open fun handlerAdapter(webSocketService: WebSocketService): WebSocketHandlerAdapter? {
        return WebSocketHandlerAdapter(webSocketService)
    }

    @Bean
    @ConditionalOnMissingBean
    open fun monoReactiveDataFetcherResultProcessor(): MonoDataFetcherResultProcessor {
        return MonoDataFetcherResultProcessor()
    }

    @Bean
    @ConditionalOnMissingBean
    open fun fluxReactiveDataFetcherResultProcessor(): FluxDataFetcherResultProcessor {
        return FluxDataFetcherResultProcessor()
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    open class WebFluxArgumentHandlerConfiguration {

        @Qualifier
        private annotation class Dgs

        @Dgs
        @Bean
        open fun dgsBindingContext(adapter: ObjectProvider<RequestMappingHandlerAdapter>): BindingContext {
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
                RequestHeaderMapMethodArgumentResolver(registry),
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
                RequestHeaderMethodArgumentResolver(beanFactory, registry),
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
                RequestParamMethodArgumentResolver(
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
                RequestParamMapMethodArgumentResolver(registry),
                bindingContext
            )
        }
    }
}
