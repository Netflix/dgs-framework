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
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.graphql.GraphQlProperties
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.graphql.execution.DataFetcherExceptionResolver
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.GraphQlSource
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import org.springframework.graphql.execution.SchemaReport
import org.springframework.graphql.execution.SelfDescribingDataFetcher
import org.springframework.graphql.execution.SubscriptionExceptionResolver
import java.util.function.Consumer
import java.util.stream.Collectors

@AutoConfiguration
@AutoConfigureBefore(name = ["org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration"])
@AutoConfigureAfter(name = ["com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration"])
open class DgsSpringGraphQLSourceAutoConfiguration(
    private val dgsGraphQLConfigProps: DgsGraphQLConfigurationProperties,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(DgsSpringGraphQLAutoConfiguration::class.java)
    }

    @Bean
    open fun graphQlSource(
        properties: GraphQlProperties,
        dgsSchemaProvider: DgsSchemaProvider,
        exceptionResolvers: ObjectProvider<DataFetcherExceptionResolver>,
        subscriptionExceptionResolvers: ObjectProvider<SubscriptionExceptionResolver>,
        instrumentations: ObjectProvider<Instrumentation>,
        wiringConfigurers: ObjectProvider<RuntimeWiringConfigurer>,
        sourceCustomizers: ObjectProvider<GraphQlSourceBuilderCustomizer>,
        reloadSchemaIndicator: DefaultDgsQueryExecutor.ReloadSchemaIndicator,
        defaultExceptionHandler: DataFetcherExceptionHandler,
        reportConsumer: Consumer<SchemaReport>?,
    ): GraphQlSource {
        val dataFetcherExceptionResolvers: MutableList<DataFetcherExceptionResolver> =
            exceptionResolvers
                .orderedStream()
                .collect(Collectors.toList())
        dataFetcherExceptionResolvers += ExceptionHandlerResolverAdapter(defaultExceptionHandler)

        val builder =
            DgsGraphQLSourceBuilder(dgsSchemaProvider, dgsGraphQLConfigProps.introspection.showSdlComments)
                .exceptionResolvers(dataFetcherExceptionResolvers)
                .subscriptionExceptionResolvers(subscriptionExceptionResolvers.orderedStream().toList())
                .instrumentation(instrumentations.orderedStream().toList())

        if (properties.schema.inspection.isEnabled) {
            if (reportConsumer != null) {
                builder.inspectSchemaMappings(reportConsumer)
            } else if (logger.isInfoEnabled) {
                builder.inspectSchemaMappings { schemaReport ->
                    val messageBuilder = StringBuilder("***Schema Report***\n")

                    val arguments =
                        schemaReport.unmappedArguments().map { entry ->
                            val (key, value) = entry
                            if (key is SelfDescribingDataFetcher) {
                                val dataFetcher =
                                    (key as DgsGraphQLSourceBuilder.DgsSelfDescribingDataFetcher).dataFetcher
                                dataFetcher.method.declaringClass.name + "." + dataFetcher.method.name + " for arguments " + value
                            } else {
                                entry.toString()
                            }
                        }

                    messageBuilder.append("Unmapped fields: ").append(schemaReport.unmappedFields()).append('\n')
                    messageBuilder.append("Unmapped registrations: ").append(schemaReport.unmappedRegistrations()).append('\n')
                    messageBuilder.append("Unmapped arguments: ").append(arguments).append('\n')
                    messageBuilder.append("Skipped types: ").append(schemaReport.skippedTypes()).append('\n')

                    logger.info("{}", messageBuilder)
                }
            }
        }

        wiringConfigurers.orderedStream().forEach { configurer: RuntimeWiringConfigurer ->
            builder.configureRuntimeWiring(configurer)
        }
        sourceCustomizers.orderedStream().forEach { customizer: GraphQlSourceBuilderCustomizer ->
            customizer.customize(builder)
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
