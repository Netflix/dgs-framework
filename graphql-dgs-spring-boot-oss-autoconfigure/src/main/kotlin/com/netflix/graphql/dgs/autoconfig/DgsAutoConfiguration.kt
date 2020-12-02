package com.netflix.graphql.dgs.autoconfig

import com.netflix.graphql.dgs.DgsContextBuilder
import com.netflix.graphql.dgs.DgsFederationResolver
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.context.DgsCustomContextBuilder
import com.netflix.graphql.dgs.exceptions.DefaultDataFetcherExceptionHandler
import com.netflix.graphql.dgs.internal.DefaultDgsGraphQLContextBuilder
import com.netflix.graphql.dgs.internal.DefaultDgsQueryExecutor
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.logging.LogEvent
import com.netflix.graphql.dgs.logging.LogService
import com.netflix.graphql.mocking.MockProvider
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.ExecutionStrategy
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLSchema
import graphql.schema.idl.TypeDefinitionRegistry
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(DgsAutoConfiguration::class.java)

    @Bean
    open fun dgsQueryExecutor(applicationContext: ApplicationContext, schema: GraphQLSchema, schemaProvider: DgsSchemaProvider, dgsDataLoaderProvider: DgsDataLoaderProvider, dgsContextBuilder: DgsContextBuilder, dataFetcherExceptionHandler: DataFetcherExceptionHandler, chainedInstrumentation: ChainedInstrumentation, environment: Environment, @Qualifier("query") providedQueryExecutionStrategy: Optional<ExecutionStrategy>, @Qualifier("mutation") providedMutationExecutionStrategy: Optional<ExecutionStrategy>): DgsQueryExecutor {
        val hotReloadSetting = environment.getProperty("dgs.reload")
        val isLaptopProfile = environment.activeProfiles.contains("laptop")

        val enableReload = when (hotReloadSetting) {
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

        val queryExecutionStrategy = providedQueryExecutionStrategy.orElse(AsyncExecutionStrategy(dataFetcherExceptionHandler))
        val mutationExecutionStrategy = providedMutationExecutionStrategy.orElse(AsyncSerialExecutionStrategy(dataFetcherExceptionHandler))
        return DefaultDgsQueryExecutor(schema, schemaProvider, dgsDataLoaderProvider, dgsContextBuilder, chainedInstrumentation, enableReload, queryExecutionStrategy, mutationExecutionStrategy)
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

    @Bean
    @ConditionalOnMissingBean
    open fun dgsSchemaProvider(applicationContext: ApplicationContext, federationResolver: Optional<DgsFederationResolver>, dataFetcherExceptionHandler: DataFetcherExceptionHandler, existingTypeDefinitionFactory: Optional<TypeDefinitionRegistry>, existingCodeRegistry: Optional<GraphQLCodeRegistry>, mockProviders: Optional<Set<MockProvider>>): DgsSchemaProvider {
        return DgsSchemaProvider(applicationContext, federationResolver, existingTypeDefinitionFactory, mockProviders)
    }

    @Bean
    @ConditionalOnMissingBean
    open fun dataFetcherExceptionHandler(): DataFetcherExceptionHandler {
        return DefaultDataFetcherExceptionHandler()
    }

    @Bean
    @ConditionalOnMissingBean
    open fun schema(dgsSchemaProvider: DgsSchemaProvider) : GraphQLSchema {
        return dgsSchemaProvider.schema()
    }

    @Bean
    @ConditionalOnMissingBean
    open fun graphQLContextBuilder(dgsCustomContextBuilder: Optional<DgsCustomContextBuilder<*>>) : DgsContextBuilder {
        return DefaultDgsGraphQLContextBuilder(dgsCustomContextBuilder)
    }

    @Bean
    @ConditionalOnMissingBean
    open fun basicLogService(environment: Environment): LogService {
        return object : LogService {
            override fun publishLog(logEvent: LogEvent) {
                log.debug(logEvent.toString())
            }
        }
    }


}
