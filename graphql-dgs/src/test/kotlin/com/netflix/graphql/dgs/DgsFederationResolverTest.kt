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

package com.netflix.graphql.dgs

import com.netflix.graphql.dgs.federation.DefaultDgsFederationResolver
import graphql.TypeResolutionEnvironment
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DgsFederationResolverTest {

    @Test
    fun defaultTypeResolver() {

        val schema = """
            type Query {}
            
            type Movie {
               movieID: ID
               title: String
            }
        """.trimIndent()

        val graphQLSchema: GraphQLSchema = buildGraphQLSchema(schema)

        val type = DefaultDgsFederationResolver().typeResolver().getType(TypeResolutionEnvironment(Movie("123", "Stranger Things"), emptyMap(), null, null, graphQLSchema, null))
        assertThat(type.name).isEqualTo("Movie")
    }

    @Test
    fun typeNotFound() {

        val schema = """
            type Query {}
        """.trimIndent()

        val graphQLSchema: GraphQLSchema = buildGraphQLSchema(schema)

        val type = DefaultDgsFederationResolver().typeResolver().getType(TypeResolutionEnvironment(Movie("123", "Stranger Things"), emptyMap(), null, null, graphQLSchema, null))
        assertThat(type).isNull()
    }

    @Test
    fun mappedType() {
        val schema = """
            type Query {}
            
            #Represented by Java type Movie, but with a different name
            type DgsMovie {
               movieID: ID
               title: String
            }
        """.trimIndent()

        val graphQLSchema: GraphQLSchema = buildGraphQLSchema(schema)
        val customTypeResolver = object: DefaultDgsFederationResolver() {
            override fun typeMapping(): Map<Class<*>, String> {
                return mapOf(Pair(Movie::class.java, "DgsMovie"))
            }
        }

        val type = customTypeResolver.typeResolver().getType(TypeResolutionEnvironment(Movie("123", "Stranger Things"), emptyMap(), null, null, graphQLSchema, null))
        assertThat(type.name).isEqualTo("DgsMovie")
    }

    private fun buildGraphQLSchema(schema: String): GraphQLSchema {
        val schemaParser = SchemaParser()
        val registry = schemaParser.parse(schema)
        val schemaGenerator = SchemaGenerator()

        return schemaGenerator.makeExecutableSchema(registry, RuntimeWiring.newRuntimeWiring().build())
    }
}

data class Movie(val movieId:String, val title:String)