/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.graphql.dgs

import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import graphql.TrivialDataFetcher
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import java.util.Optional
import java.util.function.Supplier

class TrivialDataFetcherTest {
    private val application = GenericApplicationContext()
    private val schemaProvider =
        DgsSchemaProvider(
            applicationContext = application,
            federationResolver = Optional.empty(),
            existingTypeDefinitionRegistry = Optional.empty(),
            methodDataFetcherFactory = MethodDataFetcherFactory(argumentResolvers = listOf()),
        )

    @Test
    fun `DgsData annotated method with trivial field set to true is registered as TrivialDataFetcher`() {
        @DgsComponent
        class Component {
            @DgsData(parentType = "Foo", field = "bar", trivial = true)
            fun trivialDataFetcher(): String = "bar"

            @DgsData(parentType = "Foo", field = "baz", trivial = false)
            fun nonTrivialDataFetcher(): String = "baz"
        }

        application.registerBean(Component::class.java, Supplier { Component() })
        application.refresh()
        val schema =
            schemaProvider
                .schema(
                    """
                    type Query {
                      foo: Foo
                    }

                    type Foo {
                      bar: String
                      baz: String
                    }
                    """.trimIndent(),
                ).graphQLSchema
        val parentType = schema.getTypeAs<GraphQLObjectType>("Foo") ?: fail("Parent type not found")
        val definitions = parentType.children.filterIsInstance<GraphQLFieldDefinition>()
        val barDefinition = definitions.find { it.name == "bar" } ?: fail("bar definition not found")
        val bazDefinition = definitions.find { it.name == "baz" } ?: fail("baz definition not found")
        val barFetcher = schema.codeRegistry.getDataFetcher(parentType, barDefinition)
        val bazFetcher = schema.codeRegistry.getDataFetcher(parentType, bazDefinition)

        assertThat(barFetcher).isInstanceOf(TrivialDataFetcher::class.java)
        assertThat(bazFetcher).isNotInstanceOf(TrivialDataFetcher::class.java)
    }

    @Test
    fun `DgsQuery annotated method with trivial field set to true is registered as TrivialDataFetcher`() {
        @DgsComponent
        class Component {
            @DgsQuery(field = "foo", trivial = true)
            fun trivialDataFetcher(): String = "foo"

            @DgsQuery(field = "bar", trivial = false)
            fun nonTrivialDataFetcher(): String = "bar"
        }

        application.registerBean(Component::class.java, Supplier { Component() })
        application.refresh()
        val schema =
            schemaProvider
                .schema(
                    """
                    type Query {
                      foo: String!
                      bar: String!
                    }
                    """.trimIndent(),
                ).graphQLSchema

        val parentType = schema.queryType
        val definitions = parentType.children.filterIsInstance<GraphQLFieldDefinition>()
        val fooDefinition = definitions.find { it.name == "foo" } ?: fail("foo definition not found")
        val barDefinition = definitions.find { it.name == "bar" } ?: fail("bar definition not found")
        val fooFetcher = schema.codeRegistry.getDataFetcher(parentType, fooDefinition)
        val barFetcher = schema.codeRegistry.getDataFetcher(parentType, barDefinition)

        assertThat(fooFetcher).isInstanceOf(TrivialDataFetcher::class.java)
        assertThat(barFetcher).isNotInstanceOf(TrivialDataFetcher::class.java)
    }

    @Test
    fun `DgsDataList annotated method with trivial field set to true is registered as TrivialDataFetcher`() {
        @DgsComponent
        class Component {
            @DgsData.List(
                DgsData(parentType = "Query", field = "foo", trivial = true),
                DgsData(parentType = "Query", field = "bar", trivial = false),
            )
            fun dataFetcher(): String = "foo"
        }

        application.registerBean(Component::class.java, Supplier { Component() })
        application.refresh()
        val schema =
            schemaProvider
                .schema(
                    """
                    type Query {
                      foo: String!
                      bar: String!
                    }
                    """.trimIndent(),
                ).graphQLSchema

        val parentType = schema.queryType
        val definitions = parentType.children.filterIsInstance<GraphQLFieldDefinition>()
        val fooDefinition = definitions.find { it.name == "foo" } ?: fail("foo definition not found")
        val barDefinition = definitions.find { it.name == "bar" } ?: fail("bar definition not found")
        val fooFetcher = schema.codeRegistry.getDataFetcher(parentType, fooDefinition)
        val barFetcher = schema.codeRegistry.getDataFetcher(parentType, barDefinition)

        assertThat(fooFetcher).isInstanceOf(TrivialDataFetcher::class.java)
        assertThat(barFetcher).isNotInstanceOf(TrivialDataFetcher::class.java)
    }
}
