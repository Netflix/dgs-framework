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

package com.netflix.graphql.dgs.internal

import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.mapper.MappingException
import com.netflix.graphql.dgs.*
import com.netflix.graphql.dgs.exceptions.DgsQueryExecutionDataExtractionException
import com.netflix.graphql.dgs.exceptions.QueryException
import com.netflix.graphql.types.errors.ErrorType
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.instrumentation.ChainedInstrumentation
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.dataloader.DataLoaderRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpHeaders
import java.time.LocalDateTime
import java.util.*
import java.util.function.Supplier

@ExtendWith(MockKExtension::class)
internal class DefaultDgsQueryExecutorTest {

    @MockK
    lateinit var applicationContextMock: ApplicationContext

    @MockK
    lateinit var dgsDataLoaderProvider: DgsDataLoaderProvider

    var dgsQueryExecutor: DefaultDgsQueryExecutor? = null

    @BeforeEach
    fun createExecutor() {

        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun hello(): String {
                return "hi!"
            }
        }

        val numbersFetcher = object : Any() {
            @DgsData(parentType = "Query", field = "numbers")
            fun hello(): List<Int> {
                return listOf(1, 2, 3)
            }
        }

        val moviesFetcher = object : Any() {
            @DgsData(parentType = "Query", field = "movies")
            fun movies(): List<Movie> {
                return listOf(Movie("Extraction", LocalDateTime.MIN), Movie("Da 5 Bloods", LocalDateTime.MAX))
            }
        }

        val fetcherWithError = object : Any() {
            @DgsData(parentType = "Query", field = "withError")
            fun withError(): String {
                throw RuntimeException("Broken!")
            }
        }

        val echoFetcher = object : Any() {
            @DgsData(parentType = "Query", field = "echo")
            fun echo(@InputArgument("message") message: String): String {
                return message
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            ),
            Pair("numbersFetcher", numbersFetcher),
            Pair("moviesFetcher", moviesFetcher),
            Pair("withErrorFetcher", fetcherWithError),
            Pair("echoFetcher", echoFetcher)
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns mapOf(
            Pair(
                "DateTimeScalar",
                LocalDateTimeScalar()
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
        every { dgsDataLoaderProvider.buildRegistryWithContextSupplier(any<Supplier<Any>>()) } returns DataLoaderRegistry()

        val provider = DgsSchemaProvider(
            applicationContextMock,
            federationResolver = Optional.empty(),
            dataFetcherExceptionHandler = Optional.empty(),
            existingTypeDefinitionRegistry = Optional.empty(),
            mockProviders = Optional.empty()
        )

        val schema = provider.schema(
            """
            type Query {
                hello: String
                numbers: [Int]
                movies: [Movie]
                withError: String
                echo(message: String): String
            }
            
            type Movie { 
                title: String
                releaseDate: DateTime
            }
            
            type Person {
                name: String
            }

            scalar DateTime
            """.trimIndent()
        )

        dgsQueryExecutor = DefaultDgsQueryExecutor(
            schema,
            provider,
            dgsDataLoaderProvider,
            DefaultDgsGraphQLContextBuilder(Optional.empty()),
            ChainedInstrumentation(),
            AsyncExecutionStrategy(),
            AsyncSerialExecutionStrategy(),
            Optional.empty()
        )
    }

    @Test
    fun `Returns a GraphQL Error wth BAD_REQUEST described in the extensions`() {
        val result = dgsQueryExecutor!!.execute(" ")
        assertThat(result)
            .isNotNull
            .extracting { it.errors.first().extensions["errorType"] }
            .isEqualTo(ErrorType.BAD_REQUEST.name)
    }

    @Test
    fun extractJsonWithString() {
        val helloResult = dgsQueryExecutor!!.executeAndExtractJsonPath<String>(
            """
            {
                hello
            }
            """.trimIndent(),
            "data.hello"
        )

        assertThat(helloResult).isEqualTo("hi!")
    }

    @Test
    fun extractJsonWithListOfString() {
        val numbers = dgsQueryExecutor!!.executeAndExtractJsonPath<List<Int>>(
            """
            {
                numbers
            }
            """.trimIndent(),
            "data.numbers"
        )

        assertThat(numbers).isEqualTo(listOf(1, 2, 3))
    }

    @Test
    fun extractJsonWithObjectListAsMap() {
        val movies = dgsQueryExecutor!!.executeAndExtractJsonPath<List<Map<String, Any>>>(
            """
            {
                movies { title releaseDate }
            }
            """.trimIndent(),
            "data.movies"
        )

        assertThat(movies[0]["title"]).isEqualTo("Extraction")
        assertThat(LocalDateTime.parse(movies[0]["releaseDate"] as CharSequence)).isEqualTo(LocalDateTime.MIN)
    }

    @Test
    fun extractJsonAsObjectAsMap() {
        val movie = dgsQueryExecutor!!.executeAndExtractJsonPath<Map<String, Any>>(
            """
            {
                movies { title releaseDate }
            }
            """.trimIndent(),
            "data.movies[0]"
        )

        assertThat(movie["title"]).isEqualTo("Extraction")
        assertThat(LocalDateTime.parse(movie["releaseDate"] as CharSequence)).isEqualTo(LocalDateTime.MIN)
    }

    @Test
    fun extractJsonAsObject() {
        val movie = dgsQueryExecutor!!.executeAndExtractJsonPathAsObject(
            """
            {
                movies { title releaseDate }
            }
            """.trimIndent(),
            "data.movies[0]", Movie::class.java
        )

        assertThat(movie.title).isEqualTo("Extraction")
        assertThat(movie.releaseDate).isEqualTo(LocalDateTime.MIN)
    }

    @Test
    fun extractJsonAsObjectWithTypeRef() {
        val person = dgsQueryExecutor!!.executeAndExtractJsonPathAsObject(
            """
            {
                movies { title releaseDate }
            }
            """.trimIndent(),
            "data.movies", object : TypeRef<List<Movie>>() {}
        )

        assertThat(person).isInstanceOf(List::class.java)
        assertThat(person[0]).isExactlyInstanceOf(Movie::class.java)
    }

    @Test
    fun extractJsonAsObjectTypeRefWithVariables() {
        val expectedMessage = "hello dgs"
        val message = dgsQueryExecutor!!.executeAndExtractJsonPathAsObject(
            "query echo(\$message: String) { echo(message: \$message)}",
            "data.echo",
            mapOf("message" to expectedMessage),
            object : TypeRef<String>() {}
        )

        assertThat(message).isEqualTo(expectedMessage)
    }

    @Test
    fun extractJsonAsObjectTypeRefWithHeadersNotThrow() {
        val httpHeaders = HttpHeaders()
        httpHeaders.add("test", "headerValue")

        val expectedMessage = "hello dgs"
        val message = dgsQueryExecutor!!.executeAndExtractJsonPathAsObject(
            "query echo(\$message: String) { echo(message: \$message)}",
            "data.echo",
            mapOf("message" to expectedMessage),
            object : TypeRef<String>() {},
            httpHeaders
        )

        assertThat(message).isEqualTo(expectedMessage)
    }

    @Test
    fun extractJsonAsObjectClazzWithVariables() {
        val expectedMessage = "hello dgs"
        val message = dgsQueryExecutor!!.executeAndExtractJsonPathAsObject(
            "query echo(\$message: String) { echo(message: \$message)}",
            "data.echo",
            mapOf("message" to expectedMessage),
            String::class.java
        )

        assertThat(message).isEqualTo(expectedMessage)
    }

    @Test
    fun extractJsonAsObjectClazzWithHeadersNotThrow() {
        val httpHeaders = HttpHeaders()
        httpHeaders.add("test", "headerValue")

        val expectedMessage = "hello dgs"
        val message = dgsQueryExecutor!!.executeAndExtractJsonPathAsObject(
            "query echo(\$message: String) { echo(message: \$message)}",
            "data.echo",
            mapOf("message" to expectedMessage),
            String::class.java,
            httpHeaders
        )

        assertThat(message).isEqualTo(expectedMessage)
    }

    @Test
    fun extractError() {
        val queryException = assertThrows<QueryException> {
            dgsQueryExecutor!!.executeAndExtractJsonPath<String>(
                """
            {
                withError            
            }
                """.trimIndent(),
                "data.withError"
            )
        }

        assertThat(queryException.message).contains("Broken!")
    }

    @Test
    fun extractJsonAsObjectError() {
        val assertThrows = assertThrows<DgsQueryExecutionDataExtractionException> {
            dgsQueryExecutor!!.executeAndExtractJsonPathAsObject(
                """
            {
                movies { title } 
            }
                """.trimIndent(),
                "data.movies[0]", String::class.java
            )
        }

        assertThat(assertThrows.message).isEqualTo("Error deserializing data from '{\"data\":{\"movies\":[{\"title\":\"Extraction\"},{\"title\":\"Da 5 Bloods\"}]}}' with JsonPath 'data.movies[0]' and target class java.lang.String")
        assertThat(assertThrows.cause).isInstanceOf(MappingException::class.java)
        assertThat(assertThrows.jsonResult).isEqualTo("{\"data\":{\"movies\":[{\"title\":\"Extraction\"},{\"title\":\"Da 5 Bloods\"}]}}")
        assertThat(assertThrows.jsonPath).isEqualTo("data.movies[0]")
        assertThat(assertThrows.targetClass).isEqualTo(String::class.java.name)
    }

    @Test
    fun extractJsonAsTypeRefError() {
        val assertThrows = assertThrows<DgsQueryExecutionDataExtractionException> {
            dgsQueryExecutor!!.executeAndExtractJsonPathAsObject(
                """
            {
                movies { title } 
            }
                """.trimIndent(),
                "data.movies[0]", object : TypeRef<List<String>>() {}
            )
        }

        assertThat(assertThrows.message).isEqualTo("Error deserializing data from '{\"data\":{\"movies\":[{\"title\":\"Extraction\"},{\"title\":\"Da 5 Bloods\"}]}}' with JsonPath 'data.movies[0]' and target class java.util.List<? extends java.lang.String>")
        assertThat(assertThrows.cause).isInstanceOf(MappingException::class.java)
        assertThat(assertThrows.jsonResult).isEqualTo("{\"data\":{\"movies\":[{\"title\":\"Extraction\"},{\"title\":\"Da 5 Bloods\"}]}}")
        assertThat(assertThrows.jsonPath).isEqualTo("data.movies[0]")
        assertThat(assertThrows.targetClass).isEqualTo("java.util.List<? extends java.lang.String>")
    }

    @Test
    fun documentContext() {
        val context = dgsQueryExecutor!!.executeAndGetDocumentContext(
            """
            {
                movies { title releaseDate }
            }
            """.trimIndent()
        )

        val movieList = context.read("data.movies", object : TypeRef<List<Movie>>() {})
        assertThat(movieList.size).isEqualTo(2)
        val movie = context.read("data.movies[0]", Movie::class.java)
        assertThat(movie).isNotNull
    }

    @Test
    fun documentContextWithTypename() {
        val context = dgsQueryExecutor!!.executeAndGetDocumentContext(
            """
            {
                movies { title __typename }
            }
            """.trimIndent()
        )

        val movie = context.read("data.movies[0]", Movie::class.java)
        assertThat(movie).isNotNull
    }

    @Test
    fun withFieldNamedErrors() {

        val context = dgsQueryExecutor!!.executeAndGetDocumentContext(
            """
            {
                movies { title __typename }
            }
            """.trimIndent()
        )

        val movie = context.read("data.movies[0]", Movie::class.java)
        assertThat(movie).isNotNull
    }
}

data class Movie(val title: String, val releaseDate: LocalDateTime?)
