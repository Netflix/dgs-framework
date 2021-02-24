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

package com.netflix.graphql.dgs.autoconfig

import com.netflix.graphql.dgs.DgsContextBuilder
import com.netflix.graphql.dgs.DgsFederationResolver
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.context.DgsCustomContextBuilder
import com.netflix.graphql.dgs.exceptions.DefaultDataFetcherExceptionHandler
import com.netflix.graphql.dgs.internal.DefaultDgsGraphQLContextBuilder
import com.netflix.graphql.dgs.internal.DefaultDgsQueryExecutor
import com.netflix.graphql.dgs.internal.DefaultDgsQueryExecutor.ReloadSchemaIndicator
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.scalars.UploadScalar
import com.netflix.graphql.mocking.MockProvider
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.ExecutionStrategy
import graphql.execution.ExecutionIdProvider
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLSchema
import graphql.schema.idl.TypeDefinitionRegistry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.util.*

/**
 * Framework auto configuration based on open source Spring only, without Netflix integrations.
 * This does NOT have logging, tracing, metrics and security integration.
 */
@Configuration
@ImportAutoConfiguration(classes = [JacksonAutoConfiguration::class])
open class DgsAutoConfiguration {

    @Bean
    open fun dgsQueryExecutor(applicationContext: ApplicationContext,
                              schema: GraphQLSchema,
                              schemaProvider: DgsSchemaProvider,
                              dgsDataLoaderProvider: DgsDataLoaderProvider,
                              dgsContextBuilder: DgsContextBuilder,
                              dataFetcherExceptionHandler: DataFetcherExceptionHandler,
                              chainedInstrumentation: ChainedInstrumentation,
                              environment: Environment,
                              @Qualifier("query") providedQueryExecutionStrategy: Optional<ExecutionStrategy>,
                              @Qualifier("mutation") providedMutationExecutionStrategy: Optional<ExecutionStrategy>,
                              idProvider: Optional<ExecutionIdProvider>,
                              reloadSchemaIndicator: ReloadSchemaIndicator
    ): DgsQueryExecutor {


        val queryExecutionStrategy = providedQueryExecutionStrategy.orElse(AsyncExecutionStrategy(dataFetcherExceptionHandler))
        val mutationExecutionStrategy = providedMutationExecutionStrategy.orElse(AsyncSerialExecutionStrategy(dataFetcherExceptionHandler))
        return DefaultDgsQueryExecutor(
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
    open fun dgsDataLoaderProvider(applicationContext: ApplicationContext): DgsDataLoaderProvider {
        return DgsDataLoaderProvider(applicationContext)
    }

    @Bean
    open fun dgsInstrumentation(instrumentation: Optional<List<Instrumentation>>): ChainedInstrumentation {
        val listOfInstrumentations = instrumentation.orElse(emptyList())
        return ChainedInstrumentation(listOfInstrumentations)
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
        val hotReloadSetting = environment.getProperty("dgs.reload")

        return ReloadSchemaIndicator {
            when (hotReloadSetting) {
                "true" -> {
                    true
                }
                "false" -> {
                    false
                }
                else -> {
                    isLaptopProfile
                }
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean
    open fun dgsSchemaProvider(
            applicationContext: ApplicationContext,
            federationResolver: Optional<DgsFederationResolver>,
            dataFetcherExceptionHandler: DataFetcherExceptionHandler,
            existingTypeDefinitionFactory: Optional<TypeDefinitionRegistry>,
            existingCodeRegistry: Optional<GraphQLCodeRegistry>,
            mockProviders: Optional<Set<MockProvider>>
    ): DgsSchemaProvider {
        return DgsSchemaProvider(applicationContext, federationResolver, existingTypeDefinitionFactory, mockProviders)
    }

    @Bean
    @ConditionalOnMissingBean
    open fun dataFetcherExceptionHandler(): DataFetcherExceptionHandler {
        return DefaultDataFetcherExceptionHandler()
    }

    @Bean
    @ConditionalOnMissingBean
    open fun schema(dgsSchemaProvider: DgsSchemaProvider): GraphQLSchema {
        return dgsSchemaProvider.schema()
    }

    @Bean
    @ConditionalOnMissingBean
    open fun graphQLContextBuilder(dgsCustomContextBuilder: Optional<DgsCustomContextBuilder<*>>): DgsContextBuilder {
        return DefaultDgsGraphQLContextBuilder(dgsCustomContextBuilder)
    }

    @Bean
    open fun uploadScalar(): UploadScalar {
        return UploadScalar()
    }
}
