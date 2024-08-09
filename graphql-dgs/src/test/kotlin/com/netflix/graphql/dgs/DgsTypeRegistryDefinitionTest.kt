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

package com.netflix.graphql.dgs

import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import graphql.GraphQL
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeExtensionDefinition
import graphql.language.TypeName
import graphql.schema.idl.TypeDefinitionRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.Optional
import kotlin.reflect.KClass

class DgsTypeRegistryDefinitionTest {
    private val contextRunner = ApplicationContextRunner()

    /**
     * Note that there is an existing schema in resources/schema/schema.graphqls.
     */
    @Test
    fun `A TypeDefinitionRegistry should be able to merge with a file based schema`() {
        @DgsComponent
        class FetcherWithRegistry {
            @DgsTypeDefinitionRegistry
            fun types(): TypeDefinitionRegistry {
                val newRegistry = TypeDefinitionRegistry()

                val query =
                    ObjectTypeExtensionDefinition
                        .newObjectTypeExtensionDefinition()
                        .name("Query")
                        .fieldDefinition(
                            FieldDefinition
                                .newFieldDefinition()
                                .name("dynamicField")
                                .type(TypeName("String"))
                                .build(),
                        ).build()

                newRegistry.add(query)

                return newRegistry
            }
        }

        @DgsComponent
        class Fetcher {
            @DgsData(parentType = "Query", field = "dynamicField")
            fun dynamicField(): String = "hello from dgs"
        }

        contextRunner.withBeans(FetcherWithRegistry::class, Fetcher::class).run { context ->
            val provider =
                DgsSchemaProvider(
                    applicationContext = context,
                    federationResolver = Optional.empty(),
                    existingTypeDefinitionRegistry = Optional.empty(),
                    methodDataFetcherFactory = MethodDataFetcherFactory(listOf()),
                )

            val schema = provider.schema().graphQLSchema
            val graphql = GraphQL.newGraphQL(schema).build()
            val result =
                graphql.execute(
                    """
                    {
                        dynamicField
                    }
                    """.trimIndent(),
                )

            val data = result.getData<Map<String, Any>>()
            assertThat(data["dynamicField"]).isEqualTo("hello from dgs")
        }
    }

    @Test
    fun `Multiple TypeDefinitionRegistry methods should be able to merge`() {
        @DgsComponent
        class RegistryComponent {
            @DgsTypeDefinitionRegistry
            fun types(): TypeDefinitionRegistry {
                val newRegistry = TypeDefinitionRegistry()

                val query =
                    ObjectTypeExtensionDefinition
                        .newObjectTypeExtensionDefinition()
                        .name("Query")
                        .fieldDefinition(
                            FieldDefinition
                                .newFieldDefinition()
                                .name("dynamicField")
                                .type(TypeName("String"))
                                .build(),
                        ).build()

                newRegistry.add(query)

                return newRegistry
            }
        }

        @DgsComponent
        class AnotherRegistryComponent {
            @DgsTypeDefinitionRegistry
            fun types(): TypeDefinitionRegistry {
                val newRegistry = TypeDefinitionRegistry()

                val query =
                    ObjectTypeExtensionDefinition
                        .newObjectTypeExtensionDefinition()
                        .name("Query")
                        .fieldDefinition(
                            FieldDefinition
                                .newFieldDefinition()
                                .name("number")
                                .type(TypeName("Int"))
                                .build(),
                        ).build()

                newRegistry.add(query)

                return newRegistry
            }
        }

        @DgsComponent
        class QueryFetcher {
            @DgsData(parentType = "Query", field = "dynamicField")
            fun dynamicField(): String = "hello from dgs"
        }

        @DgsComponent
        class NumberFetcher {
            @DgsData(parentType = "Query", field = "number")
            fun dynamicField(): Int = 1
        }

        contextRunner
            .withBeans(
                RegistryComponent::class,
                AnotherRegistryComponent::class,
                QueryFetcher::class,
                NumberFetcher::class,
            ).run { context ->
                val provider =
                    DgsSchemaProvider(
                        applicationContext = context,
                        federationResolver = Optional.empty(),
                        existingTypeDefinitionRegistry = Optional.empty(),
                        methodDataFetcherFactory = MethodDataFetcherFactory(listOf()),
                    )

                val schema = provider.schema().graphQLSchema
                val graphql = GraphQL.newGraphQL(schema).build()
                val result =
                    graphql.execute(
                        """
                        {
                            dynamicField
                            number
                        }
                        """.trimIndent(),
                    )

                val data = result.getData<Map<String, Any>>()
                assertThat(data["dynamicField"]).isEqualTo("hello from dgs")
                assertThat(data["number"]).isEqualTo(1)
            }
    }

    private fun ApplicationContextRunner.withBeans(vararg beanClasses: KClass<*>): ApplicationContextRunner {
        var context = this
        for (klazz in beanClasses) {
            context = context.withBean(klazz.java)
        }
        return context
    }
}
