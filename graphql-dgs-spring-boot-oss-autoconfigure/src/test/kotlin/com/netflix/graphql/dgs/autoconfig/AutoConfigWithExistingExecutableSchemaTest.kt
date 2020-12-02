/*
 * Copyright 2020 Netflix, Inc.
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

import com.netflix.graphql.dgs.DgsQueryExecutor
import graphql.Scalars.GraphQLString
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates.coordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLCodeRegistry.newCodeRegistry
import graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLObjectType.newObject
import graphql.schema.GraphQLSchema
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


class AutoConfigWithExistingExecutableSchemaTest {
    private val context = WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(DgsAutoConfiguration::class.java))!!

    @Configuration
    open class ConfigWithSchema {
        @Bean
        open fun schema() : GraphQLSchema {
            val helloDataFetcher: DataFetcher<String> = DataFetcher { "Hello" }

            val objectType: GraphQLObjectType = newObject()
                    .name("QueryType")
                    .field(newFieldDefinition()
                            .name("hello")
                            .type(GraphQLString)
                    )
                    .build()

            val codeRegistry: GraphQLCodeRegistry = newCodeRegistry()
                    .dataFetcher(
                            coordinates("QueryType", "hello"),
                            helloDataFetcher)
                    .build()

            return GraphQLSchema.newSchema().query(objectType).codeRegistry(codeRegistry).build()
        }
    }

    @Test
    fun existingSchema() {
        context.withUserConfiguration(ConfigWithSchema::class.java).run { ctx ->
            Assertions.assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.executeAndExtractJsonPath<String>("query {  hello }", "data.hello")
            }.isEqualTo("Hello")
        }
    }
}