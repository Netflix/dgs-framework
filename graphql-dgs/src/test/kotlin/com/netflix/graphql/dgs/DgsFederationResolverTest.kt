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

import com.apollographql.federation.graphqljava._Entity
import com.netflix.graphql.dgs.exceptions.MissingDgsEntityFetcherException
import com.netflix.graphql.dgs.federation.DefaultDgsFederationResolver
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import graphql.TypeResolutionEnvironment
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingEnvironmentImpl
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import java.util.*
import java.util.concurrent.CompletableFuture

@Suppress("UNCHECKED_CAST")
@ExtendWith(MockKExtension::class)
class DgsFederationResolverTest {
    @MockK
    lateinit var applicationContextMock: ApplicationContext

    lateinit var dgsSchemaProvider:DgsSchemaProvider

    @BeforeEach
    fun setup() {
        dgsSchemaProvider = DgsSchemaProvider(
                applicationContextMock,
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
        )
    }

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

        val type = DefaultDgsFederationResolver(dgsSchemaProvider).typeResolver().getType(TypeResolutionEnvironment(Movie("123", "Stranger Things"), emptyMap(), null, null, graphQLSchema, null))
        assertThat(type.name).isEqualTo("Movie")
    }

    @Test
    fun typeNotFound() {

        val schema = """
            type Query {}
        """.trimIndent()

        val graphQLSchema: GraphQLSchema = buildGraphQLSchema(schema)

        val type = DefaultDgsFederationResolver(dgsSchemaProvider).typeResolver().getType(TypeResolutionEnvironment(Movie("123", "Stranger Things"), emptyMap(), null, null, graphQLSchema, null))
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
        val customTypeResolver = object: DefaultDgsFederationResolver(dgsSchemaProvider) {
            override fun typeMapping(): Map<Class<*>, String> {
                return mapOf(Pair(Movie::class.java, "DgsMovie"))
            }
        }

        val type = customTypeResolver.typeResolver().getType(TypeResolutionEnvironment(Movie("123", "Stranger Things"), emptyMap(), null, null, graphQLSchema, null))
        assertThat(type.name).isEqualTo("DgsMovie")
    }

    @Test
    fun missingDgsEntityFetcher() {
        val arguments = mapOf<String,Any>(Pair(_Entity.argumentName, listOf(mapOf(Pair("__typename", "Movie")))))
        val dataFetchingEnvironment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build()
        assertThrows<MissingDgsEntityFetcherException> { DefaultDgsFederationResolver(dgsSchemaProvider).entitiesFetcher().get(dataFetchingEnvironment) }
    }

    @Test
    fun missingTypeNameForEntitiesQuery() {
        val arguments = mapOf<String,Any>(Pair(_Entity.argumentName, listOf(mapOf(Pair("something", "Else")))))
        val dataFetchingEnvironment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build()
        assertThrows<RuntimeException> { DefaultDgsFederationResolver(dgsSchemaProvider).entitiesFetcher().get(dataFetchingEnvironment) }
    }

    @Test
    fun dgsEntityFetcher() {
        val movieEntityFetcher = object {
            @DgsEntityFetcher(name = "Movie")
            fun movieEntityFetcher(values: Map<String, Any>): Movie {
                return Movie(values["movieId"].toString())
            }
        }

        testEntityFetcher(movieEntityFetcher)

    }

    @Test
    fun dgsEntityFetcherWithDataFetchingEnv() {
        val movieEntityFetcher = object {
            @DgsEntityFetcher(name = "Movie")
            fun movieEntityFetcher(values: Map<String, Any>, dfe: DataFetchingEnvironment?): Movie {
                if(dfe == null) {
                    throw RuntimeException()
                }
                return Movie(values["movieId"].toString())
            }
        }

        testEntityFetcher(movieEntityFetcher)

    }

    @Test
    fun dgsEntityFetcherWithDgsDataFetchingEnv() {
        val movieEntityFetcher = object {
            @DgsEntityFetcher(name = "Movie")
            fun movieEntityFetcher(values: Map<String, Any>, dfe: DgsDataFetchingEnvironment?): Movie {
                if(dfe == null) {
                    throw RuntimeException()
                }
                return Movie(values["movieId"].toString())
            }
        }

        testEntityFetcher(movieEntityFetcher)
    }

    @Test
    fun dgsEntityFetcherMissingArgument() {
        val movieEntityFetcher = object {
            @DgsEntityFetcher(name = "Movie")
            fun movieEntityFetcher(): Movie {
                throw RuntimeException("Should not be called")
            }
        }
        val arguments = mapOf<String,Any>(Pair(_Entity.argumentName, listOf(mapOf(Pair("__typename", "Movie")))))
        val dataFetchingEnvironment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build()
        assertThrows<RuntimeException> { DefaultDgsFederationResolver(dgsSchemaProvider).entitiesFetcher().get(dataFetchingEnvironment) }
    }

    private fun testEntityFetcher(movieEntityFetcher: Any) {
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("MovieEntityFetcher", movieEntityFetcher))
        dgsSchemaProvider.schema("""type Query {}""")

        val arguments = mapOf<String,Any>(Pair(_Entity.argumentName, listOf(mapOf(Pair("__typename", "Movie"), Pair("movieId", "1")))))
        val dataFetchingEnvironment = DgsDataFetchingEnvironment(DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build())

        val result = (DefaultDgsFederationResolver(dgsSchemaProvider).entitiesFetcher().get(dataFetchingEnvironment) as CompletableFuture<List<*>>).get()
        assertThat(result).isNotNull
        assertThat(result.size).isEqualTo(1)
        assertThat(result[0] is Movie).isTrue
        assertThat((result[0] as Movie).movieId).isEqualTo("1")
    }

    private fun buildGraphQLSchema(schema: String): GraphQLSchema {
        val schemaParser = SchemaParser()
        val registry = schemaParser.parse(schema)
        val schemaGenerator = SchemaGenerator()

        return schemaGenerator.makeExecutableSchema(registry, RuntimeWiring.newRuntimeWiring().build())
    }
}

data class Movie(val movieId:String = "", val title:String = "")