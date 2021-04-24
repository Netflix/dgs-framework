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

package com.netflix.graphql.dgs.reactive

import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.mapper.MappingException
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsScalar
import com.netflix.graphql.dgs.exceptions.DgsQueryExecutionDataExtractionException
import com.netflix.graphql.dgs.exceptions.QueryException
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveGraphQLContextBuilder
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveQueryExecutor
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
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import reactor.test.StepVerifier
import java.time.LocalDateTime
import java.util.*
import java.util.function.Supplier

@ExtendWith(MockKExtension::class)
internal class DefaultDgsReactiveQueryExecutorTest {
    @MockK
    lateinit var applicationContextMock: ApplicationContext

    @MockK
    lateinit var dgsDataLoaderProvider: DgsDataLoaderProvider

    lateinit var dgsQueryExecutor: DefaultDgsReactiveQueryExecutor

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

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            ),
            Pair("numbersFetcher", numbersFetcher),
            Pair("moviesFetcher", moviesFetcher),
            Pair("withErrorFetcher", fetcherWithError)
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns mapOf(
            Pair(
                "DateTimeScalar",
                LocalDateTimeScalar()
            )
        )
        every { dgsDataLoaderProvider.buildRegistryWithContextSupplier(any<Supplier<Any>>()) } returns DataLoaderRegistry()

        val provider = DgsSchemaProvider(
            applicationContextMock,
            federationResolver = Optional.empty(),
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

        dgsQueryExecutor = DefaultDgsReactiveQueryExecutor(
            schema, provider, dgsDataLoaderProvider,
            DefaultDgsReactiveGraphQLContextBuilder(
                Optional.empty()
            ),
            ChainedInstrumentation(), AsyncExecutionStrategy(), AsyncSerialExecutionStrategy(), Optional.empty()
        )
    }

    @Test
    fun extractJsonWithString() {
        val helloResult = dgsQueryExecutor.executeAndExtractJsonPath<String>(
            """
            {
                hello
            }
            """.trimIndent(),
            "data.hello"
        )

        StepVerifier.create(helloResult).assertNext {
            assertThat(it).isEqualTo("hi!")
        }.verifyComplete()
    }

    @Test
    fun extractJsonWithListOfString() {
        val numbers = dgsQueryExecutor.executeAndExtractJsonPath<List<Int>>(
            """
            {
                numbers
            }
            """.trimIndent(),
            "data.numbers"
        )

        StepVerifier.create(numbers).assertNext {
            assertThat(it).isEqualTo(listOf(1, 2, 3))
        }
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

        StepVerifier.create(movies).assertNext {
            assertThat(it[0]["title"]).isEqualTo("Extraction")
            assertThat(LocalDateTime.parse(it[0]["releaseDate"] as CharSequence))
                .isEqualTo(LocalDateTime.MIN)
        }.verifyComplete()
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

        StepVerifier.create(movie).assertNext {
            assertThat(it["title"]).isEqualTo("Extraction")
            assertThat(LocalDateTime.parse(it["releaseDate"] as CharSequence)).isEqualTo(LocalDateTime.MIN)
        }.verifyComplete()
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

        StepVerifier.create(movie).assertNext {
            assertThat(it.title).isEqualTo("Extraction")
            assertThat(it.releaseDate).isEqualTo(LocalDateTime.MIN)
        }.verifyComplete()
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

        StepVerifier.create(person).assertNext {
            assertThat(it).isInstanceOf(List::class.java)
            assertThat(it[0]).isExactlyInstanceOf(Movie::class.java)
        }.verifyComplete()
    }

    @Test
    fun extractError() {
        val withError = dgsQueryExecutor.executeAndExtractJsonPath<String>(
            """
            {
                withError
            }
            """.trimIndent(),
            "data.withError"
        )

        StepVerifier.create(withError).verifyError(QueryException::class.java)
    }

    @Test
    fun extractJsonAsObjectError() {

        val withError = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
            """
            {
                movies { title }
            }
            """.trimIndent(),
            "data.movies[0]", String::class.java
        )

        StepVerifier.create(withError).consumeErrorWith {
            assertThat(it).isInstanceOf(DgsQueryExecutionDataExtractionException::class.java)
            if (it is DgsQueryExecutionDataExtractionException) {
                assertThat(it.message).isEqualTo("Error deserializing data from '{\"data\":{\"movies\":[{\"title\":\"Extraction\"},{\"title\":\"Da 5 Bloods\"}]}}' with JsonPath 'data.movies[0]' and target class java.lang.String")
                assertThat(it.cause).isInstanceOf(MappingException::class.java)

                assertThat(it.jsonResult).isEqualTo("{\"data\":{\"movies\":[{\"title\":\"Extraction\"},{\"title\":\"Da 5 Bloods\"}]}}")
                assertThat(it.jsonPath).isEqualTo("data.movies[0]")
                assertThat(it.targetClass).isEqualTo(String::class.java.name)
            }
        }.verify()
    }

    @Test
    fun extractJsonAsTypeRefError() {
        val withError = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
            """
            {
                movies { title }
            }
            """.trimIndent(),
            "data.movies[0]", object : TypeRef<List<String>>() {}
        )

        StepVerifier.create(withError).consumeErrorWith {
            assertThat(it).isInstanceOf(DgsQueryExecutionDataExtractionException::class.java)
            if (it is DgsQueryExecutionDataExtractionException) {
                assertThat(it.message).isEqualTo("Error deserializing data from '{\"data\":{\"movies\":[{\"title\":\"Extraction\"},{\"title\":\"Da 5 Bloods\"}]}}' with JsonPath 'data.movies[0]' and target class java.util.List<? extends java.lang.String>")
                assertThat(it.cause).isInstanceOf(MappingException::class.java)
                assertThat(it.jsonResult).isEqualTo("{\"data\":{\"movies\":[{\"title\":\"Extraction\"},{\"title\":\"Da 5 Bloods\"}]}}")
                assertThat(it.jsonPath).isEqualTo("data.movies[0]")
                assertThat(it.targetClass).isEqualTo("java.util.List<? extends java.lang.String>")
            }
        }.verify()
    }

    @Test
    fun documentContext() {
        val context = dgsQueryExecutor.executeAndGetDocumentContext(
            """
            {
                movies { title releaseDate }
            }
            """.trimIndent()
        )

        StepVerifier.create(context).assertNext {
            val movieList = it.read("data.movies", object : TypeRef<List<Movie>>() {})
            assertThat(movieList.size).isEqualTo(2)
            val movie = it.read("data.movies[0]", Movie::class.java)
            assertThat(movie).isNotNull
        }.verifyComplete()
    }

    @Test
    fun documentContextWithTypename() {
        val context = dgsQueryExecutor.executeAndGetDocumentContext(
            """
            {
                movies { title __typename }
            }
            """.trimIndent()
        )

        StepVerifier.create(context).assertNext {
            val movie = it.read("data.movies[0]", Movie::class.java)
            assertThat(movie).isNotNull
        }.verifyComplete()
    }
}

data class Movie(val title: String, val releaseDate: LocalDateTime?)
