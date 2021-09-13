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
import graphql.GraphQL
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeExtensionDefinition
import graphql.language.TypeName
import graphql.schema.idl.TypeDefinitionRegistry
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import java.util.*

@ExtendWith(MockKExtension::class)
class DgsTypeRegistryDefinitionTest {
    @MockK
    lateinit var applicationContextMock: ApplicationContext

    /**
     * Note that there is an existing schema in resources/schema/schema.graphqls.
     */
    @Test
    fun `A TypeDefinitionRegistry should be able to merge with a file based schema`() {
        val typeRegistry = object : Any() {
            @DgsTypeDefinitionRegistry
            fun types(): TypeDefinitionRegistry {
                val newRegistry = TypeDefinitionRegistry()

                val query =
                    ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name("Query").fieldDefinition(
                        FieldDefinition.newFieldDefinition().name("dynamicField").type(TypeName("String")).build()
                    ).build()

                newRegistry.add(query)

                return newRegistry
            }
        }

        val queryFetcher = object : Any() {
            @DgsData(parentType = "Query", field = "dynamicField")
            fun dynamicField(): String {
                return "hello from dgs"
            }
        }

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("queryResolver", queryFetcher), Pair("typeRegistry", typeRegistry))
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val schema = provider.schema()
        val graphql = GraphQL.newGraphQL(schema).build()
        val result = graphql.execute(
            """
            {
                dynamicField
            }
            """.trimIndent()
        )

        val data = result.getData<Map<String, Any>>()
        assertThat(data["dynamicField"]).isEqualTo("hello from dgs")
    }

    @Test
    fun `Multiple TypeDefinitionRegistry methods should be able to merge`() {
        val firstTypeDefinitionFactory = object : Any() {
            @DgsTypeDefinitionRegistry
            fun types(): TypeDefinitionRegistry {
                val newRegistry = TypeDefinitionRegistry()

                val query =
                    ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name("Query").fieldDefinition(
                        FieldDefinition.newFieldDefinition().name("dynamicField").type(TypeName("String")).build()
                    ).build()

                newRegistry.add(query)

                return newRegistry
            }
        }

        val secondTypeDefinitionFactory = object : Any() {
            @DgsTypeDefinitionRegistry
            fun types(): TypeDefinitionRegistry {
                val newRegistry = TypeDefinitionRegistry()

                val query =
                    ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name("Query").fieldDefinition(
                        FieldDefinition.newFieldDefinition().name("number").type(TypeName("Int")).build()
                    ).build()

                newRegistry.add(query)

                return newRegistry
            }
        }

        val queryFetcher = object : Any() {
            @DgsData(parentType = "Query", field = "dynamicField")
            fun dynamicField(): String {
                return "hello from dgs"
            }
        }

        val numberFetcher = object : Any() {
            @DgsData(parentType = "Query", field = "number")
            fun dynamicField(): Int {
                return 1
            }
        }

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("queryResolver", queryFetcher), Pair("numberResolver", numberFetcher), Pair("typeRegistry", firstTypeDefinitionFactory), Pair("secondRegistry", secondTypeDefinitionFactory))
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val schema = provider.schema()
        val graphql = GraphQL.newGraphQL(schema).build()
        val result = graphql.execute(
            """
            {
                dynamicField
                number
            }
            """.trimIndent()
        )

        val data = result.getData<Map<String, Any>>()
        assertThat(data["dynamicField"]).isEqualTo("hello from dgs")
        assertThat(data["number"]).isEqualTo(1)
    }
}
