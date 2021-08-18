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
import com.netflix.graphql.dgs.exceptions.DefaultDataFetcherExceptionHandler
import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException
import com.netflix.graphql.dgs.federation.DefaultDgsFederationResolver
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import graphql.TypeResolutionEnvironment
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherResult
import graphql.execution.ExecutionStepInfo
import graphql.execution.ResultPath
import graphql.schema.*
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import java.util.*
import java.util.concurrent.CompletableFuture

@Suppress("UNCHECKED_CAST")
@ExtendWith(MockKExtension::class)
class DgsFederationResolverTest {
    @MockK
    lateinit var applicationContextMock: ApplicationContext

    lateinit var dgsSchemaProvider: DgsSchemaProvider

    lateinit var dgsExceptionHandler: DataFetcherExceptionHandler

    @BeforeEach
    fun setup() {
        dgsExceptionHandler = DefaultDataFetcherExceptionHandler()
        dgsSchemaProvider = DgsSchemaProvider(
            applicationContextMock,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            dataFetcherExceptionHandler = Optional.of(dgsExceptionHandler)
        )
    }

    @Test
    fun defaultTypeResolver() {

        val schema = """
            type Query {
                something: String #Empty queries are not allowed
            }
            
            type Movie {
               movieID: ID
               title: String
            }
        """.trimIndent()

        val graphQLSchema: GraphQLSchema = buildGraphQLSchema(schema)

        val type = DefaultDgsFederationResolver(dgsSchemaProvider, Optional.of(dgsExceptionHandler)).typeResolver().getType(TypeResolutionEnvironment(Movie("123", "Stranger Things"), emptyMap(), null, null, graphQLSchema, null))
        assertThat(type.name).isEqualTo("Movie")
    }

    @Test
    fun typeNotFound() {

        val schema = """
            type Query {
                something: String
            }
        """.trimIndent()

        val graphQLSchema: GraphQLSchema = buildGraphQLSchema(schema)

        val type = DefaultDgsFederationResolver(dgsSchemaProvider, Optional.of(dgsExceptionHandler)).typeResolver().getType(TypeResolutionEnvironment(Movie("123", "Stranger Things"), emptyMap(), null, null, graphQLSchema, null))
        assertThat(type).isNull()
    }

    @Test
    fun mappedType() {
        val schema = """
            type Query {
                something: String #Empty queries are not allowed
            }
            
            #Represented by Java type Movie, but with a different name
            type DgsMovie {
               movieID: ID
               title: String
            }
        """.trimIndent()

        val graphQLSchema: GraphQLSchema = buildGraphQLSchema(schema)
        val customTypeResolver = object : DefaultDgsFederationResolver(dgsSchemaProvider, Optional.of(dgsExceptionHandler)) {
            override fun typeMapping(): Map<Class<*>, String> {
                return mapOf(Pair(Movie::class.java, "DgsMovie"))
            }
        }

        val type = customTypeResolver.typeResolver().getType(TypeResolutionEnvironment(Movie("123", "Stranger Things"), emptyMap(), null, null, graphQLSchema, null))
        assertThat(type.name).isEqualTo("DgsMovie")
    }

    @Test
    fun missingDgsEntityFetcher() {
        val arguments = mapOf<String, Any>(Pair(_Entity.argumentName, listOf(mapOf(Pair("__typename", "Movie")))))
        val dataFetchingEnvironment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build()
        val result = (DefaultDgsFederationResolver(dgsSchemaProvider, Optional.of(dgsExceptionHandler)).entitiesFetcher().get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>)
        assertThat(result).isNotNull
        assertThat(result.get().data.size).isEqualTo(1)
        assertThat(result.get().errors.size).isEqualTo(1)
        assertThat(result.get().errors[0].message).contains("MissingDgsEntityFetcherException")
    }

    @Test
    fun missingTypeNameForEntitiesQuery() {
        val arguments = mapOf<String, Any>(Pair(_Entity.argumentName, listOf(mapOf(Pair("something", "Else")))))
        val dataFetchingEnvironment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build()
        val result = (DefaultDgsFederationResolver(dgsSchemaProvider, Optional.of(dgsExceptionHandler)).entitiesFetcher().get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>)
        assertThat(result).isNotNull
        assertThat(result.get().data.size).isEqualTo(1)
        assertThat(result.get().errors.size).isEqualTo(1)
        assertThat(result.get().errors[0].message).contains("RuntimeException")
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
    fun dgsEntityFetcherReturningNull() {
        val movieEntityFetcher = object {
            @DgsEntityFetcher(name = "Movie")
            fun movieEntityFetcher(values: Map<String, Any>): Movie? {
                return null
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("MovieEntityFetcher", movieEntityFetcher))
        dgsSchemaProvider.schema("""type Query {}""")

        val arguments = mapOf<String, Any>(Pair(_Entity.argumentName, listOf(mapOf(Pair("__typename", "Movie"), Pair("movieId", "1")))))
        val dataFetchingEnvironment = DgsDataFetchingEnvironment(DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build())

        val result = (DefaultDgsFederationResolver(dgsSchemaProvider, Optional.of(dgsExceptionHandler)).entitiesFetcher().get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>)
        assertThat(result).isNotNull
        assertThat(result.get().data.size).isEqualTo(1)
    }

    @Test
    fun dgsEntityFetcherWithDataFetchingEnv() {
        val movieEntityFetcher = object {
            @DgsEntityFetcher(name = "Movie")
            fun movieEntityFetcher(values: Map<String, Any>, dfe: DataFetchingEnvironment?): Movie {
                if (dfe == null) {
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
                if (dfe == null) {
                    throw RuntimeException()
                }
                return Movie(values["movieId"].toString())
            }
        }

        testEntityFetcher(movieEntityFetcher)
    }

    @Test
    fun dgsEntityFetcherWithException() {

        val movieEntityFetcher = object {
            @DgsEntityFetcher(name = "Movie")
            fun movieEntityFetcher(values: Map<String, Any>): Movie {
                if (values["movieId"] == "invalid") {
                    throw DgsInvalidInputArgumentException("Invalid input argument exception")
                }

                return Movie(values["movieId"].toString())
            }
        }
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("MovieEntityFetcher", movieEntityFetcher))
        dgsSchemaProvider.schema("""type Query {}""")

        val arguments = mapOf<String, Any>(Pair(_Entity.argumentName, listOf(mapOf(Pair("__typename", "Movie"), Pair("movieId", "invalid")))))
        val executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().path(ResultPath.parse("/_entities")).type(GraphQLList.list(GraphQLUnionType.newUnionType().name("Entity").possibleTypes(GraphQLObjectType.newObject().name("Movie").build()).build())).build()
        val dataFetchingEnvironment = DgsDataFetchingEnvironment(DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).executionStepInfo(executionStepInfo).build())
        val result = (DefaultDgsFederationResolver(dgsSchemaProvider, Optional.of(dgsExceptionHandler)).entitiesFetcher().get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>)
        assertThat(result).isNotNull
        assertThat(result.get().data.size).isEqualTo(1)
        assertThat(result.get().errors.size).isEqualTo(1)
        assertThat(result.get().errors[0].message).contains("DgsInvalidInputArgumentException")
    }

    @Test
    fun dgsEntityFetcherWithIllegalArgumentException() {

        val movieEntityFetcher = object {
            @DgsEntityFetcher(name = "Movie")
            fun movieEntityFetcher(values: Map<String, Any>, illegalArgument: Int): Movie {
                return Movie()
            }
        }
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("MovieEntityFetcher", movieEntityFetcher))
        dgsSchemaProvider.schema("""type Query {}""")

        val arguments = mapOf<String, Any>(Pair(_Entity.argumentName, listOf(mapOf(Pair("__typename", "Movie"), Pair("movieId", "invalid"), Pair("illegalArgument", 0)))))
        val dataFetchingEnvironment = DgsDataFetchingEnvironment(DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build())
        val result = (DefaultDgsFederationResolver(dgsSchemaProvider, Optional.of(dgsExceptionHandler)).entitiesFetcher().get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>)
        assertThat(result).isNotNull
        assertThat(result.get().data.size).isEqualTo(1)
        assertThat(result.get().errors.size).isEqualTo(1)
        assertThat(result.get().errors[0].message).contains("IllegalArgumentException")
    }

    @Test
    fun dgsEntityFetcherMissingArgument() {
        val arguments = mapOf<String, Any>(Pair(_Entity.argumentName, listOf(mapOf(Pair("__typename", "Movie")))))
        val dataFetchingEnvironment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build()
        val result = (DefaultDgsFederationResolver(dgsSchemaProvider, Optional.of(dgsExceptionHandler)).entitiesFetcher().get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>)
        assertThat(result).isNotNull
        assertThat(result.get().data.size).isEqualTo(1)
        assertThat(result.get().errors.size).isEqualTo(1)
        assertThat(result.get().errors[0].message).contains("MissingDgsEntityFetcherException")
    }

    private fun testEntityFetcher(movieEntityFetcher: Any) {
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("MovieEntityFetcher", movieEntityFetcher))
        dgsSchemaProvider.schema("""type Query {}""")

        val arguments = mapOf<String, Any>(Pair(_Entity.argumentName, listOf(mapOf(Pair("__typename", "Movie"), Pair("movieId", "1")))))
        val dataFetchingEnvironment = DgsDataFetchingEnvironment(DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build())

        val result = (DefaultDgsFederationResolver(dgsSchemaProvider, Optional.of(dgsExceptionHandler)).entitiesFetcher().get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>)
        assertThat(result).isNotNull
        assertThat(result.get().data.size).isEqualTo(1)
        assertThat(result.get().data.first() is Movie).isTrue
        assertThat((result.get().data.first() as Movie).movieId).isEqualTo("1")
    }

    private fun buildGraphQLSchema(schema: String): GraphQLSchema {
        val schemaParser = SchemaParser()
        val registry = schemaParser.parse(schema)
        val schemaGenerator = SchemaGenerator()

        return schemaGenerator.makeExecutableSchema(registry, RuntimeWiring.newRuntimeWiring().build())
    }
}

data class Movie(val movieId: String = "", val title: String = "")
