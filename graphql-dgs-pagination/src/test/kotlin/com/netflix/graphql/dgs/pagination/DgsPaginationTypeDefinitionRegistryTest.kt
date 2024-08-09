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
import graphql.schema.GraphQLTypeUtil.simplePrint
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.validation.SchemaValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class DgsPaginationTypeDefinitionRegistryTest {
    private val paginationTypeRegistry = DgsPaginationTypeDefinitionRegistry()

    @Test
    fun generatePaginatedTypes() {
        val schema =
            """
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
        val graphqlSchema = SchemaGenerator().makeExecutableSchema(typeRegistry.merge(paginatedTypeRegistry), RuntimeWiring.MOCKED_WIRING)
        assertThat(SchemaValidator().validateSchema(graphqlSchema)).isEmpty()

        val addedDirective = graphqlSchema.getDirective("connection")
        assertThat(addedDirective).isNotNull
        assertThat(addedDirective.validLocations())
            .isEqualTo(
                setOf(
                    Introspection.DirectiveLocation.OBJECT,
                    Introspection.DirectiveLocation.UNION,
                    Introspection.DirectiveLocation.INTERFACE,
                ),
            )

        val movieConnectionType = graphqlSchema.getObjectType("MovieConnection")
        assertThat(movieConnectionType).isNotNull.extracting { it.description }.isNotNull
        val movieEdgeType = graphqlSchema.getObjectType("MovieEdge")
        assertThat(movieEdgeType).isNotNull.extracting { it.description }.isNotNull
        val pageInfoType = graphqlSchema.getObjectType("PageInfo")
        assertThat(pageInfoType).isNotNull.extracting { it.description }.isNotNull

        val movieConnection = graphqlSchema.getObjectType("MovieConnection")
        val edgesField =
            movieConnection.getFieldDefinition("edges")
                ?: fail("edges field not found on $movieConnection")
        assertThat(simplePrint(edgesField.type)).isEqualTo("[MovieEdge]")
        val pageInfoField =
            movieConnection.getFieldDefinition("pageInfo")
                ?: fail("pageInfo field not found on $movieConnection")
        assertThat(simplePrint(pageInfoField.type)).isEqualTo("PageInfo!")

        val movieEdge = graphqlSchema.getObjectType("MovieEdge")
        val cursorField =
            movieEdge.getFieldDefinition("cursor")
                ?: fail("cursor field not found on $movieEdge")
        assertThat(simplePrint(cursorField.type)).isEqualTo("String")
        val nodeField =
            movieEdge.getFieldDefinition("node")
                ?: fail("node field not found on $movieEdge")
        assertThat(simplePrint(nodeField.type)).isEqualTo("Movie")

        val pageInfo = graphqlSchema.getObjectType("PageInfo")
        val hasPreviousPageField =
            pageInfo.getFieldDefinition("hasPreviousPage")
                ?: fail("hasPreviousPage field not found on $pageInfo")
        assertThat(simplePrint(hasPreviousPageField.type)).isEqualTo("Boolean!")
        val hasNextPageField =
            pageInfo.getFieldDefinition("hasNextPage")
                ?: fail("hasNextPage field not found on $pageInfo")
        assertThat(simplePrint(hasNextPageField.type)).isEqualTo("Boolean!")
        val startCursorField =
            pageInfo.getFieldDefinition("startCursor")
                ?: fail("startCursor field not found on $pageInfo")
        assertThat(simplePrint(startCursorField.type)).isEqualTo("String")
        val endCursorField =
            pageInfo.getFieldDefinition("endCursor")
                ?: fail("endCursor field not found on $pageInfo")
        assertThat(simplePrint(endCursorField.type)).isEqualTo("String")
    }

    @Test
    fun doesNotGeneratePagInfoIfExists() {
        val schema =
            """
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
        val graphqlSchema = SchemaGenerator().makeExecutableSchema(typeRegistry.merge(paginatedTypeRegistry), RuntimeWiring.MOCKED_WIRING)
        assertThat(SchemaValidator().validateSchema(graphqlSchema)).isEmpty()

        val movieConnectionType = graphqlSchema.getObjectType("MovieConnection")
        assertThat(movieConnectionType).isNotNull.extracting { it.description }.isNotNull
        val movieEdgeType = graphqlSchema.getObjectType("MovieEdge")
        assertThat(movieEdgeType).isNotNull.extracting { it.description }.isNotNull
        assertThat(paginatedTypeRegistry.types()["PageInfo"]).isNull()
    }

    @Test
    fun generateForInterfaces() {
        val schema =
            """
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
               rating: Int
            }
            """.trimIndent()

        val typeRegistry = SchemaParser().parse(schema)
        val paginatedTypeRegistry = paginationTypeRegistry.registry(typeRegistry)
        val graphqlSchema = SchemaGenerator().makeExecutableSchema(typeRegistry.merge(paginatedTypeRegistry), RuntimeWiring.MOCKED_WIRING)
        assertThat(SchemaValidator().validateSchema(graphqlSchema)).isEmpty()

        val movieConnectionType = graphqlSchema.getObjectType("IMovieConnection")
        assertThat(movieConnectionType).isNotNull.extracting { it.description }.isNotNull
        val movieEdgeType = graphqlSchema.getObjectType("IMovieEdge")
        assertThat(movieEdgeType).isNotNull.extracting { it.description }.isNotNull
        val scaryMovieConnectionType = graphqlSchema.getObjectType("ScaryMovieConnection")
        assertThat(scaryMovieConnectionType).isNotNull.extracting { it.description }.isNotNull
        val scaryMovieEdgeType = graphqlSchema.getObjectType("ScaryMovieEdge")
        assertThat(scaryMovieEdgeType).isNotNull.extracting { it.description }.isNotNull
        val pageInfoType = graphqlSchema.getObjectType("PageInfo")
        assertThat(pageInfoType).isNotNull.extracting { it.description }.isNotNull
    }

    @Test
    fun doesNotGenerateIfNotObjectOrInterfaceType() {
        val schema =
            """
            type Query {
                something: CustomScalarConnection
            }
            
            scalar CustomScalar @connection
            """.trimIndent()

        val typeRegistry = SchemaParser().parse(schema)
        val paginatedTypeRegistry = paginationTypeRegistry.registry(typeRegistry)

        assertThat(paginatedTypeRegistry.types()["CustomScalarConnection"]).isNull()
        assertThat(paginatedTypeRegistry.types()["CustomScalarEdge"]).isNull()
    }

    @Test
    fun generateForUnions() {
        val schema =
            """
            type Query {
                something: IMovieConnection
            }
            
            union IMovie @connection = ScaryMovie
            
            type ScaryMovie @connection {
               movieID: ID
               title: String
               rating: Int
            }
            """.trimIndent()

        val typeRegistry = SchemaParser().parse(schema)
        val paginatedTypeRegistry = paginationTypeRegistry.registry(typeRegistry)
        val graphqlSchema = SchemaGenerator().makeExecutableSchema(typeRegistry.merge(paginatedTypeRegistry), RuntimeWiring.MOCKED_WIRING)
        assertThat(SchemaValidator().validateSchema(graphqlSchema)).isEmpty()

        val movieConnectionType = graphqlSchema.getObjectType("IMovieConnection")
        assertThat(movieConnectionType).isNotNull.extracting { it.description }.isNotNull
        val movieEdgeType = graphqlSchema.getObjectType("IMovieEdge")
        assertThat(movieEdgeType).isNotNull.extracting { it.description }.isNotNull
        val scaryMovieConnectionType = graphqlSchema.getObjectType("ScaryMovieConnection")
        assertThat(scaryMovieConnectionType).isNotNull.extracting { it.description }.isNotNull
        val scaryMovieEdgeType = graphqlSchema.getObjectType("ScaryMovieEdge")
        assertThat(scaryMovieEdgeType).isNotNull.extracting { it.description }.isNotNull
        val pageInfoType = graphqlSchema.getObjectType("PageInfo")
        assertThat(pageInfoType).isNotNull.extracting { it.description }.isNotNull
    }
}
