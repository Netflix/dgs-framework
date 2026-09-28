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

        val addedDirective = graphqlSchema.getDirective("connection") ?: fail("connection directive not found")
        assertThat(addedDirective.validLocations())
            .isEqualTo(
                setOf(
                    Introspection.DirectiveLocation.OBJECT,
                    Introspection.DirectiveLocation.UNION,
                    Introspection.DirectiveLocation.INTERFACE,
                ),
            )

        val movieConnectionType = graphqlSchema.getObjectType("MovieConnection") ?: fail("MovieConnection type not found")
        assertThat(movieConnectionType.description).isNotNull
        val movieEdgeType = graphqlSchema.getObjectType("MovieEdge") ?: fail("MovieEdge type not found")
        assertThat(movieEdgeType.description).isNotNull
        val pageInfoType = graphqlSchema.getObjectType("PageInfo") ?: fail("PageInfo type not found")
        assertThat(pageInfoType.description).isNotNull

        val movieConnection = graphqlSchema.getObjectType("MovieConnection") ?: fail("MovieConnection type not found")
        val edgesField =
            movieConnection.getFieldDefinition("edges")
                ?: fail("edges field not found on $movieConnection")
        assertThat(simplePrint(edgesField.type)).isEqualTo("[MovieEdge]")
        val pageInfoField =
            movieConnection.getFieldDefinition("pageInfo")
                ?: fail("pageInfo field not found on $movieConnection")
        assertThat(simplePrint(pageInfoField.type)).isEqualTo("PageInfo!")

        val movieEdge = graphqlSchema.getObjectType("MovieEdge") ?: fail("MovieEdge type not found")
        val cursorField =
            movieEdge.getFieldDefinition("cursor")
                ?: fail("cursor field not found on $movieEdge")
        assertThat(simplePrint(cursorField.type)).isEqualTo("String")
        val nodeField =
            movieEdge.getFieldDefinition("node")
                ?: fail("node field not found on $movieEdge")
        assertThat(simplePrint(nodeField.type)).isEqualTo("Movie")

        val pageInfo = graphqlSchema.getObjectType("PageInfo") ?: fail("PageInfo type not found")
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

        val movieConnectionType = graphqlSchema.getObjectType("MovieConnection") ?: fail("MovieConnection type not found")
        assertThat(movieConnectionType.description).isNotNull
        val movieEdgeType = graphqlSchema.getObjectType("MovieEdge") ?: fail("MovieEdge type not found")
        assertThat(movieEdgeType.description).isNotNull
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

        val movieConnectionType = graphqlSchema.getObjectType("IMovieConnection") ?: fail("IMovieConnection type not found")
        assertThat(movieConnectionType.description).isNotNull
        val movieEdgeType = graphqlSchema.getObjectType("IMovieEdge") ?: fail("IMovieEdge type not found")
        assertThat(movieEdgeType.description).isNotNull
        val scaryMovieConnectionType =
            graphqlSchema.getObjectType("ScaryMovieConnection") ?: fail("ScaryMovieConnection type not found")
        assertThat(scaryMovieConnectionType.description).isNotNull
        val scaryMovieEdgeType = graphqlSchema.getObjectType("ScaryMovieEdge") ?: fail("ScaryMovieEdge type not found")
        assertThat(scaryMovieEdgeType.description).isNotNull
        val pageInfoType = graphqlSchema.getObjectType("PageInfo") ?: fail("PageInfo type not found")
        assertThat(pageInfoType.description).isNotNull
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

        val movieConnectionType = graphqlSchema.getObjectType("IMovieConnection") ?: fail("IMovieConnection type not found")
        assertThat(movieConnectionType.description).isNotNull
        val movieEdgeType = graphqlSchema.getObjectType("IMovieEdge") ?: fail("IMovieEdge type not found")
        assertThat(movieEdgeType.description).isNotNull
        val scaryMovieConnectionType =
            graphqlSchema.getObjectType("ScaryMovieConnection") ?: fail("ScaryMovieConnection type not found")
        assertThat(scaryMovieConnectionType.description).isNotNull
        val scaryMovieEdgeType = graphqlSchema.getObjectType("ScaryMovieEdge") ?: fail("ScaryMovieEdge type not found")
        assertThat(scaryMovieEdgeType.description).isNotNull
        val pageInfoType = graphqlSchema.getObjectType("PageInfo") ?: fail("PageInfo type not found")
        assertThat(pageInfoType.description).isNotNull
    }
}
