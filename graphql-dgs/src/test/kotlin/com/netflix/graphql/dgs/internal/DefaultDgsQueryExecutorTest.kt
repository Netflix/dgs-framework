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

package com.netflix.graphql.dgs.internal

import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.mapper.MappingException
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsScalar
import com.netflix.graphql.dgs.exceptions.DgsQueryExecutionDataExtractionException
import com.netflix.graphql.dgs.exceptions.QueryException
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.instrumentation.ChainedInstrumentation
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.*
import org.dataloader.DataLoaderRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
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
                return listOf(Movie("Extraction"), Movie("Da 5 Bloods"))
            }
        }

        val fetcherWithError = object : Any() {
            @DgsData(parentType = "Query", field = "withError")
            fun withError(): String {
                throw RuntimeException("Broken!")
            }
        }


        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", fetcher), Pair("numbersFetcher", numbersFetcher), Pair("moviesFetcher", moviesFetcher), Pair("withErrorFetcher", fetcherWithError))
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { dgsDataLoaderProvider.buildRegistryWithContextSupplier(any<Supplier<Any>>()) } returns DataLoaderRegistry()


        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = provider.schema("""
            type Query {
                hello: String
                numbers: [Int]
                movies: [Movie]
                withError: String
            }
            
            type Movie { 
                title: String
            }
            
            type Person {
                name: String
            }
        """.trimIndent())


        dgsQueryExecutor = DefaultDgsQueryExecutor(schema, provider, dgsDataLoaderProvider, DefaultDgsGraphQLContextBuilder(Optional.empty()), ChainedInstrumentation(), false, AsyncExecutionStrategy(), AsyncSerialExecutionStrategy())
    }

    @Test
    fun extractJsonWithString() {
        val helloResult = dgsQueryExecutor!!.executeAndExtractJsonPath<String>("""
            {
                hello
            }
        """.trimIndent(), "data.hello")


        assertThat(helloResult).isEqualTo("hi!")
    }

    @Test
    fun extractJsonWithListOfString() {
        val numbers = dgsQueryExecutor!!.executeAndExtractJsonPath<List<Int>>("""
            {
                numbers
            }
        """.trimIndent(), "data.numbers")


        assertThat(numbers).isEqualTo(listOf(1, 2, 3))
    }

    @Test
    fun extractJsonWithObjectListAsMap() {
        val movies = dgsQueryExecutor!!.executeAndExtractJsonPath<List<Map<String, Any>>>("""
            {
                movies { title } 
            }
        """.trimIndent(), "data.movies")


        assertThat(movies[0]["title"]).isEqualTo("Extraction")
    }

    @Test
    fun extractJsonAsObjectAsMap() {
        val movie = dgsQueryExecutor!!.executeAndExtractJsonPath<Map<String, Any>>("""
            {
                movies { title } 
            }
        """.trimIndent(), "data.movies[0]")


        assertThat(movie["title"]).isEqualTo("Extraction")
    }

    @Test
    fun extractJsonAsObject() {
        val movie = dgsQueryExecutor!!.executeAndExtractJsonPathAsObject("""
            {
                movies { title } 
            }
        """.trimIndent(), "data.movies[0]", Movie::class.java)


        assertThat(movie.title).isEqualTo("Extraction")
    }

    @Test
    fun extractJsonAsObjectWithTypeRef() {
        val person = dgsQueryExecutor!!.executeAndExtractJsonPathAsObject("""
            {
                movies { title } 
            }
        """.trimIndent(), "data.movies", object : TypeRef<List<Movie>>() {})


        assertThat(person).isInstanceOf(List::class.java)
        assertThat(person[0]).isExactlyInstanceOf(Movie::class.java)
    }

    @Test
    fun extractError() {
        val queryException = assertThrows<QueryException> {
            dgsQueryExecutor!!.executeAndExtractJsonPath<String>("""
            {
                withError            
            }
        """.trimIndent(), "data.withError")
        }

        assertThat(queryException.message).contains("Broken!")
    }

    @Test
    fun extractJsonAsObjectError() {
        val assertThrows = assertThrows<DgsQueryExecutionDataExtractionException> {
            dgsQueryExecutor!!.executeAndExtractJsonPathAsObject("""
            {
                movies { title } 
            }
        """.trimIndent(), "data.movies[0]", String::class.java)
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
            dgsQueryExecutor!!.executeAndExtractJsonPathAsObject("""
            {
                movies { title } 
            }
        """.trimIndent(), "data.movies[0]", object: TypeRef<List<String>>() {})
        }

        assertThat(assertThrows.message).isEqualTo("Error deserializing data from '{\"data\":{\"movies\":[{\"title\":\"Extraction\"},{\"title\":\"Da 5 Bloods\"}]}}' with JsonPath 'data.movies[0]' and target class java.util.List<? extends java.lang.String>")
        assertThat(assertThrows.cause).isInstanceOf(MappingException::class.java)
        assertThat(assertThrows.jsonResult).isEqualTo("{\"data\":{\"movies\":[{\"title\":\"Extraction\"},{\"title\":\"Da 5 Bloods\"}]}}")
        assertThat(assertThrows.jsonPath).isEqualTo("data.movies[0]")
        assertThat(assertThrows.targetClass).isEqualTo("java.util.List<? extends java.lang.String>")
    }

    @Test
    fun documentContext() {
        val context = dgsQueryExecutor!!.executeAndGetDocumentContext("""
            {
                movies { title }
            }
        """.trimIndent())

        val movie = context.read("data.movies[0]", Movie::class.java)
        assertThat(movie).isNotNull
    }

    @Test
    fun documentContextWithTypename() {
        val context = dgsQueryExecutor!!.executeAndGetDocumentContext("""
            {
                movies { title __typename }
            }
        """.trimIndent())

        val movie = context.read("data.movies[0]", Movie::class.java)
        assertThat(movie).isNotNull
    }

    fun withFieldNamedErrors() {

        val context = dgsQueryExecutor!!.executeAndGetDocumentContext("""
            {
                movies { title __typename }
            }
        """.trimIndent())

        val movie = context.read("data.movies[0]", Movie::class.java)
        assertThat(movie).isNotNull
    }
}

data class Movie(val title: String)