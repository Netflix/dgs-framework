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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.graphql.dgs.internal.DefaultDgsQueryExecutor
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.reactive.DgsReactiveCustomContextBuilderWithRequest
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveGraphQLContextBuilder
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveQueryExecutor
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.execution.*
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.introspection.IntrospectionQuery
import graphql.schema.GraphQLSchema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.MediaType
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.function.server.*
import org.springframework.web.reactive.function.server.RequestPredicates.accept
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.ServerResponse.permanentRedirect
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.net.URI
import java.util.*

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
@EnableWebFlux
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
        reloadSchemaIndicator: DefaultDgsQueryExecutor.ReloadSchemaIndicator
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
            reloadSchemaIndicator
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
        return RouterFunctions.route().GET("/graphiql") {
            permanentRedirect(URI.create("/graphiql/index.html")).build()
        }.build()
    }

    @Bean
    open fun dgsGraphQlRouter(dgsQueryExecutor: DgsReactiveQueryExecutor): RouterFunction<ServerResponse> {
        val graphQlHandler = GraphQlHandler(dgsQueryExecutor)

        return RouterFunctions.route()
            .POST(
                configProps.path, accept(MediaType.APPLICATION_JSON, MediaType.valueOf("application/graphql")),
                graphQlHandler::graphql
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

        val simpleUrlHandlerMapping = SimpleUrlHandlerMapping(mapOf("/subscriptions" to DgsReactiveWebsocketHandler(dgsReactiveQueryExecutor)))
        simpleUrlHandlerMapping.order = 1
        return simpleUrlHandlerMapping
    }

    @Bean
    open fun handlerAdapter(): WebSocketHandlerAdapter? {
        return WebSocketHandlerAdapter()
    }

//    @Bean
//    open fun websocketHandler(dgsQueryExecutor: DgsQueryExecutor): DgsWebSocketHandler {
//        return DgsWebSocketHandler(dgsQueryExecutor)
//    }

    class GraphQlHandler(private val dgsQueryExecutor: DgsReactiveQueryExecutor) {
        val logger: Logger = LoggerFactory.getLogger(GraphQlHandler::class.java)
        val mapper = jacksonObjectMapper()

        fun graphql(request: ServerRequest): Mono<ServerResponse> {
            val executionResult: Mono<ExecutionResult> =

                request.bodyToMono(String::class.java)
                    .map {
                        if ("application/graphql" == request.headers().firstHeader("Content-Type")) {
                            QueryInput(it)
                        } else {
                            val readValue = mapper.readValue<Map<String, Any>>(it)
                            QueryInput(
                                readValue["query"] as String,
                                readValue.getOrDefault("variables", emptyMap<String, Any>()) as Map<String, Any>,
                                readValue.getOrDefault("extensions", emptyMap<String, Any>()) as Map<String, Any>
                            )
                        }
                    }
                    .flatMap { queryInput ->
                        logger.debug("Parsed variables: {}", queryInput.queryVariables)

                        dgsQueryExecutor.execute(
                            queryInput.query,
                            queryInput.queryVariables,
                            queryInput.extensions,
                            request.headers().asHttpHeaders(),
                            "",
                            request
                        )
                    }.subscribeOn(Schedulers.parallel())

            return executionResult.flatMap { result ->
                val graphQlOutput = result.toSpecification()
                ok().bodyValue(graphQlOutput)
            }
        }
    }

    data class QueryInput(
        val query: String,
        val queryVariables: Map<String, Any> = emptyMap(),
        val extensions: Map<String, Any> = emptyMap()
    )
}
