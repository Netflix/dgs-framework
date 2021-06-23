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

package com.netflix.graphql.dgs.springgraphql.bridge.autoconfig

import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.context.DgsCustomContextBuilder
import com.netflix.graphql.dgs.context.DgsCustomContextBuilderWithRequest
import com.netflix.graphql.dgs.springgraphql.bridge.DgsExecutionInputConfigurer
import com.netflix.graphql.dgs.springgraphql.bridge.DgsGraphQLSource
import graphql.schema.GraphQLSchema
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.boot.GraphQlProperties
import java.util.*

@Configuration
@EnableConfigurationProperties(GraphQlProperties::class)
open class SpringGraphQLBridgeAutoConfiguration {


    @Bean
    open fun dgsGraphQLSource(graphqlSchema: GraphQLSchema, dgsQueryExecutor: DgsQueryExecutor): DgsGraphQLSource {
        return DgsGraphQLSource(graphqlSchema, dgsQueryExecutor)
    }

    @Bean
    open fun dgsExecutionInputConfigurer(dgsCustomContextBuilder: Optional<DgsCustomContextBuilder<*>>, dgsCustomContextBuilderWithRequest: Optional<DgsCustomContextBuilderWithRequest<*>>): DgsExecutionInputConfigurer {
        return DgsExecutionInputConfigurer(dgsCustomContextBuilder, dgsCustomContextBuilderWithRequest)
    }
}