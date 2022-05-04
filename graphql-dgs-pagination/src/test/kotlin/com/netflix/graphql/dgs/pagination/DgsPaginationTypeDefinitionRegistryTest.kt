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

package com.netflix.graphql.dgs.pagination

import graphql.introspection.Introspection
import graphql.language.*
import graphql.schema.idl.SchemaParser
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class DgsPaginationTypeDefinitionRegistryTest {

    lateinit var paginationTypeRegistry: DgsPaginationTypeDefinitionRegistry

    @BeforeEach
    fun setup() {
        paginationTypeRegistry = DgsPaginationTypeDefinitionRegistry()
    }

    @Test
    fun generatePaginatedTypes() {
        val schema = """
            type Query {
                something: MovieConnection
            }
            
            type Movie @connection {
               movieID: ID
               title: String
            }
        """.trimIndent()

        val typeRegistry = SchemaParser().parse(schema)
        val paginatedTypeRegistry = paginationTypeRegistry.registry(typeRegistry)

        val addedDirective = paginatedTypeRegistry.directiveDefinitions["connection"]
        assertThat(addedDirective).isNotNull
        assertThat(addedDirective!!.directiveLocations[0].name).isEqualTo(Introspection.DirectiveLocation.OBJECT.name)

        assertThat(paginatedTypeRegistry.types()["MovieConnection"]).isNotNull
        assertThat(paginatedTypeRegistry.types()["MovieEdge"]).isNotNull
        assertThat(paginatedTypeRegistry.types()["PageInfo"]).isNotNull

        val movieConnection = (paginatedTypeRegistry.types()["MovieConnection"] as ObjectTypeDefinition)
        val edgesField = movieConnection.fieldDefinitions.find { it.name == "edges" } as FieldDefinition
        assertThat(edgesField.type.toString()).isEqualTo(ListType(TypeName("MovieEdge")).toString())
        val pageInfoField = movieConnection.fieldDefinitions.find { it.name == "pageInfo" } as FieldDefinition
        assertThat(pageInfoField.type.toString()).isEqualTo(TypeName("PageInfo").toString())

        val movieEdge = (paginatedTypeRegistry.types()["MovieEdge"] as ObjectTypeDefinition)
        val cursorField = movieEdge.fieldDefinitions.find { it.name == "cursor" } as FieldDefinition
        assertThat(cursorField.type.toString()).isEqualTo(TypeName("String").toString())
        val nodeField = movieEdge.fieldDefinitions.find { it.name == "node" } as FieldDefinition
        assertThat(nodeField.type.toString()).isEqualTo(TypeName("Movie").toString())

        val pageInfo = (paginatedTypeRegistry.types()["PageInfo"] as ObjectTypeDefinition)
        val hasPreviousPageField = pageInfo.fieldDefinitions.find { it.name == "hasPreviousPage" } as FieldDefinition
        assertThat(hasPreviousPageField.type.toString()).isEqualTo(NonNullType(TypeName("Boolean")).toString())
        val hasNextPageField = pageInfo.fieldDefinitions.find { it.name == "hasNextPage" } as FieldDefinition
        assertThat(hasNextPageField.type.toString()).isEqualTo(NonNullType(TypeName("Boolean")).toString())
        val startCursorField = pageInfo.fieldDefinitions.find { it.name == "startCursor" } as FieldDefinition
        assertThat(startCursorField.type.toString()).isEqualTo(TypeName("String").toString())
        val endCursorField = pageInfo.fieldDefinitions.find { it.name == "endCursor" } as FieldDefinition
        assertThat(endCursorField.type.toString()).isEqualTo(TypeName("String").toString())
    }

    @Test
    fun doesNotGeneratePagInfoIfExists() {
        val schema = """
            type Query {
                something: MovieConnection
            }
            
            type Movie @connection {
               movieID: ID
               title: String
            }
            
            type PageInfo {
                hasPreviousPage: Boolean!
                hasNextPage: Boolean!
                startCursor: String
                endCursor: String
            }
        """.trimIndent()

        val typeRegistry = SchemaParser().parse(schema)
        val paginatedTypeRegistry = paginationTypeRegistry.registry(typeRegistry)

        assertThat(paginatedTypeRegistry.types()["MovieConnection"]).isNotNull
        assertThat(paginatedTypeRegistry.types()["MovieEdge"]).isNotNull
        assertThat(paginatedTypeRegistry.types()["PageInfo"]).isNull()
    }

    @Test
    fun generateForInterfaces() {
        val schema = """
            type Query {
                something: IMovieConnection
            }
            
            interface IMovie @connection {
               movieID: ID
               title: String
            }
            
            type ScaryMovie implements IMovie @connection {
               movieID: ID
               title: String
               rating: Integer
            }
        """.trimIndent()

        val typeRegistry = SchemaParser().parse(schema)
        val paginatedTypeRegistry = paginationTypeRegistry.registry(typeRegistry)

        assertThat(paginatedTypeRegistry.types()["IMovieConnection"]).isNotNull
        assertThat(paginatedTypeRegistry.types()["IMovieEdge"]).isNotNull
        assertThat(paginatedTypeRegistry.types()["ScaryMovieConnection"]).isNotNull
        assertThat(paginatedTypeRegistry.types()["ScaryMovieEdge"]).isNotNull
        assertThat(paginatedTypeRegistry.types()["PageInfo"]).isNotNull
    }

    @Test
    fun doesNotGenerateIfNotObjectOrInterfaceType() {
        val schema = """
            type Query {
                something: CustomScalarConnection
            }
            
            scalar CustomScalar
        """.trimIndent()

        val typeRegistry = SchemaParser().parse(schema)
        val paginatedTypeRegistry = paginationTypeRegistry.registry(typeRegistry)

        assertThat(paginatedTypeRegistry.types()["CustomScalarConnection"]).isNull()
        assertThat(paginatedTypeRegistry.types()["CustomScalarEdge"]).isNull()
    }

    @Test
    fun generateForUnions() {
        val schema = """
            type Query {
                something: IMovieConnection
            }
            
            union IMovie @connection = ScaryMovie
            
            type ScaryMovie implements IMovie @connection {
               movieID: ID
               title: String
               rating: Integer
            }
        """.trimIndent()

        val typeRegistry = SchemaParser().parse(schema)
        val paginatedTypeRegistry = paginationTypeRegistry.registry(typeRegistry)

        assertThat(paginatedTypeRegistry.types()["IMovieConnection"]).isNotNull
        assertThat(paginatedTypeRegistry.types()["IMovieEdge"]).isNotNull
        assertThat(paginatedTypeRegistry.types()["ScaryMovieConnection"]).isNotNull
        assertThat(paginatedTypeRegistry.types()["ScaryMovieEdge"]).isNotNull
        assertThat(paginatedTypeRegistry.types()["PageInfo"]).isNotNull
    }
}
