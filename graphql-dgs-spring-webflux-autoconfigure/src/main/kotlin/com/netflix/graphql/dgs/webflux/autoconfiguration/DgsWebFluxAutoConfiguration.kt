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

import com.netflix.graphql.dgs.internal.CookieValueResolver
import com.netflix.graphql.dgs.internal.DefaultDgsQueryExecutor
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.reactive.DgsReactiveCustomContextBuilderWithRequest
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveGraphQLContextBuilder
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveQueryExecutor
import com.netflix.graphql.dgs.reactive.internal.FluxDataFetcherResultProcessor
import com.netflix.graphql.dgs.reactive.internal.MonoDataFetcherResultProcessor
import com.netflix.graphql.dgs.webflux.handlers.DefaultDgsWebfluxHttpHandler
import com.netflix.graphql.dgs.webflux.handlers.DgsHandshakeWebSocketService
import com.netflix.graphql.dgs.webflux.handlers.DgsReactiveWebsocketHandler
import com.netflix.graphql.dgs.webflux.handlers.DgsWebfluxHttpHandler
import com.netflix.graphql.dgs.webflux.handlers.WebFluxCookieValueResolver
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.ExecutionIdProvider
import graphql.execution.ExecutionStrategy
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.preparsed.PreparsedDocumentProvider
import graphql.introspection.IntrospectionQuery
import graphql.schema.GraphQLSchema
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RequestPredicates.accept
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.ServerResponse.permanentRedirect
import org.springframework.web.reactive.function.server.json
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.server.WebSocketService
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy
import reactor.core.publisher.Mono
import reactor.netty.http.server.WebsocketServerSpec
import java.net.URI
import java.util.*

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
        chainedInstrumentation: ChainedInstrumentation,
        environment: Environment,
        @Qualifier("query") providedQueryExecutionStrategy: Optional<ExecutionStrategy>,
        @Qualifier("mutation") providedMutationExecutionStrategy: Optional<ExecutionStrategy>,
        idProvider: Optional<ExecutionIdProvider>,
        reloadSchemaIndicator: DefaultDgsQueryExecutor.ReloadSchemaIndicator,
        preparsedDocumentProvider: PreparsedDocumentProvider
    ): DgsReactiveQueryExecutor {

        val queryExecutionStrategy =
            providedQueryExecutionStrategy.orElse(AsyncExecutionStrategy(dataFetcherExceptionHandler))
        val mutationExecutionStrategy =
            providedMutationExecutionStrategy.orElse(AsyncSerialExecutionStrategy(dataFetcherExceptionHandler))
        return DefaultDgsReactiveQueryExecutor(
            schema,
            schemaProvider,
            dgsDataLoaderProvider,
            dgsContextBuilder,
            chainedInstrumentation,
            queryExecutionStrategy,
            mutationExecutionStrategy,
            idProvider,
            reloadSchemaIndicator,
            preparsedDocumentProvider
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
    @ConditionalOnMissingBean
    open fun dgsWebfluxHttpHandler(dgsQueryExecutor: DgsReactiveQueryExecutor): DgsWebfluxHttpHandler {
        return DefaultDgsWebfluxHttpHandler(dgsQueryExecutor)
    }

    @Bean
    open fun dgsGraphQlRouter(dgsWebfluxHttpHandler: DgsWebfluxHttpHandler): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            .POST(
                configProps.path, accept(MediaType.APPLICATION_JSON, MediaType.valueOf("application/graphql")),
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
    open fun monoReactiveDataFetcherResultProcessor(): MonoDataFetcherResultProcessor {
        return MonoDataFetcherResultProcessor()
    }

    @Bean
    open fun fluxReactiveDataFetcherResultProcessor(): FluxDataFetcherResultProcessor {
        return FluxDataFetcherResultProcessor()
    }

    @Bean
    open fun webfluxCookieResolver(): CookieValueResolver {
        return WebFluxCookieValueResolver()
    }
}
