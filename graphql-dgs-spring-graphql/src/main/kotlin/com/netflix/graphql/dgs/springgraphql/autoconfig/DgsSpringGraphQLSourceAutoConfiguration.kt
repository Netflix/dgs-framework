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
import graphql.execution.instrumentation.Instrumentation
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.graphql.execution.DataFetcherExceptionResolver
import org.springframework.graphql.execution.GraphQlSource
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import org.springframework.graphql.execution.SubscriptionExceptionResolver

@AutoConfiguration
@AutoConfigureBefore(name = ["org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration"])
@AutoConfigureAfter(name = ["com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration"])
open class DgsSpringGraphQLSourceAutoConfiguration {
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
        val builder = DgsGraphQLSourceBuilder(dgsSchemaProvider)
            .exceptionResolvers(exceptionResolvers.orderedStream().toList())
            .subscriptionExceptionResolvers(subscriptionExceptionResolvers.orderedStream().toList())
            .instrumentation(instrumentations.orderedStream().toList())

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
        return ReloadableGraphQLSource(builder, reloadSchemaIndicator)
    }
}
