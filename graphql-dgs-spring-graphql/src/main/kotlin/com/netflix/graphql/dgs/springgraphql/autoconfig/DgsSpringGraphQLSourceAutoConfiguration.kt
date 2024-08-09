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

import com.netflix.graphql.dgs.internal.DefaultDgsQueryExecutor
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.springgraphql.DgsGraphQLSourceBuilder
import com.netflix.graphql.dgs.springgraphql.ReloadableGraphQLSource
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.instrumentation.Instrumentation
import graphql.schema.DataFetchingEnvironment
import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.graphql.GraphQlProperties
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.graphql.execution.*
import java.util.Optional
import java.util.function.Consumer

@AutoConfiguration
@AutoConfigureBefore(name = ["org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration"])
@AutoConfigureAfter(name = ["com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration"])
open class DgsSpringGraphQLSourceAutoConfiguration(
    private val dgsGraphQLConfigProps: DgsGraphQLConfigurationProperties,
) {
    private val logger = LogFactory.getLog(DgsSpringGraphQLAutoConfiguration::class.java)

    @Bean
    open fun graphQlSource(
        properties: GraphQlProperties,
        dgsSchemaProvider: DgsSchemaProvider,
        exceptionResolvers: ObjectProvider<DataFetcherExceptionResolver>,
        subscriptionExceptionResolvers: ObjectProvider<SubscriptionExceptionResolver>,
        instrumentations: ObjectProvider<Instrumentation?>,
        wiringConfigurers: ObjectProvider<RuntimeWiringConfigurer>,
        sourceCustomizers: ObjectProvider<GraphQlSourceBuilderCustomizer>,
        reloadSchemaIndicator: DefaultDgsQueryExecutor.ReloadSchemaIndicator,
        defaultExceptionHandler: DataFetcherExceptionHandler,
        reportConsumer: Optional<Consumer<SchemaReport>>,
    ): GraphQlSource {
        val dataFetcherExceptionResolvers: MutableList<DataFetcherExceptionResolver> =
            exceptionResolvers
                .orderedStream()
                .toList()
                .toMutableList()
        dataFetcherExceptionResolvers.add((ExceptionHandlerResolverAdapter(defaultExceptionHandler)))

        val builder =
            DgsGraphQLSourceBuilder(dgsSchemaProvider, dgsGraphQLConfigProps.introspection.showSdlComments)
                .exceptionResolvers(dataFetcherExceptionResolvers)
                .subscriptionExceptionResolvers(subscriptionExceptionResolvers.orderedStream().toList())
                .instrumentation(instrumentations.orderedStream().toList())

        if (properties.schema.inspection.isEnabled) {
            if (reportConsumer.isPresent) {
                builder.inspectSchemaMappings(reportConsumer.get())
            } else {
                builder.inspectSchemaMappings { message: SchemaReport? ->
                    val messageBuilder = StringBuilder("***Schema Report***\n")

                    val arguments =
                        message?.unmappedArguments()?.map {
                            if (it.key is SelfDescribingDataFetcher) {
                                val dataFetcher =
                                    (it.key as DgsGraphQLSourceBuilder.DgsSelfDescribingDataFetcher).dataFetcher
                                return@map dataFetcher.method.declaringClass.name + "." + dataFetcher.method.name + " for arguments " +
                                    it.value
                            } else {
                                return@map it.toString()
                            }
                        }

                    messageBuilder.append("Unmapped fields: ${message?.unmappedFields()}\n")
                    messageBuilder.append("Unmapped registrations: ${message?.unmappedRegistrations()}\n")
                    messageBuilder.append("Unmapped arguments: ${arguments}\n")
                    messageBuilder.append("Skipped types: ${message?.skippedTypes()}\n")

                    logger.info(messageBuilder.toString())
                }
            }
        }

        wiringConfigurers.orderedStream().forEach { configurer: RuntimeWiringConfigurer ->
            builder.configureRuntimeWiring(
                configurer,
            )
        }
        sourceCustomizers.orderedStream().forEach { customizer: GraphQlSourceBuilderCustomizer ->
            customizer.customize(
                builder,
            )
        }
        return ReloadableGraphQLSource(builder, reloadSchemaIndicator)
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
