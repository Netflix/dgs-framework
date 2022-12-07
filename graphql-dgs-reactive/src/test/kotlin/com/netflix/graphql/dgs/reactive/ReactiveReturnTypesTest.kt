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

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDirective
import com.netflix.graphql.dgs.DgsScalar
import com.netflix.graphql.dgs.exceptions.QueryException
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.FlowDataFetcherResultProcessor
import com.netflix.graphql.dgs.internal.FluxDataFetcherResultProcessor
import com.netflix.graphql.dgs.internal.MonoDataFetcherResultProcessor
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveGraphQLContextBuilder
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveQueryExecutor
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.instrumentation.SimpleInstrumentation
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.assertj.core.api.Assertions.assertThat
import org.dataloader.DataLoaderRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import reactor.util.context.Context
import reactor.util.context.ContextView
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.function.Consumer
import java.util.function.Supplier

@ExtendWith(MockKExtension::class)
internal class ReactiveReturnTypesTest {
    @MockK
    lateinit var applicationContextMock: ApplicationContext

    @MockK
    lateinit var dgsDataLoaderProvider: DgsDataLoaderProvider

    @MockK
    lateinit var stubContextConsumer: Consumer<ContextView>

    lateinit var dgsQueryExecutor: DefaultDgsReactiveQueryExecutor

    @BeforeEach
    fun createExecutor() {
        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun hello(): Mono<String> {
                return Mono.deferContextual { context ->
                    stubContextConsumer.accept(context)
                    Mono.just("hi!")
                }
            }
        }

        val numbersFetcher = object : Any() {
            @DgsData(parentType = "Query", field = "numbers")
            fun numbers(): Flux<Int> {
                return Flux.deferContextual { context ->
                    stubContextConsumer.accept(context)
                    Flux.interval(Duration.ofMillis(1)).map { it.toInt() }.take(5)
                }
            }
        }

        val moviesFetcher = object : Any() {
            @DgsData(parentType = "Query", field = "movies")
            fun movies(): Flux<Movie> {
                return Flux.just(Movie("Extraction", LocalDateTime.MIN), Movie("Da 5 Bloods", LocalDateTime.MAX))
            }
        }

        val fetcherWithError = object : Any() {
            @DgsData(parentType = "Query", field = "withError")
            fun withError(): Mono<String> {
                return Mono.error { throw RuntimeException("Broken!") }
            }
        }

        val fetcherWithFlow = object : Any() {
            @DgsData(parentType = "Query", field = "flow")
            fun withFlow(): Flow<String> {
                return flow {
                    emit("one")
                    emit("two")
                    emit("three")
                }
            }
        }

        every { stubContextConsumer.accept(any()) } just Runs

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair("helloFetcher", fetcher),
            Pair("numbersFetcher", numbersFetcher),
            Pair("moviesFetcher", moviesFetcher),
            Pair("withErrorFetcher", fetcherWithError),
            Pair("flowFetcher", fetcherWithFlow)
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
            existingTypeDefinitionRegistry = Optional.empty(),
            schemaLocations = listOf(DgsSchemaProvider.DEFAULT_SCHEMA_LOCATION),
            dataFetcherResultProcessors = listOf(
                MonoDataFetcherResultProcessor(),
                FluxDataFetcherResultProcessor(),
                FlowDataFetcherResultProcessor()
            ),
            methodDataFetcherFactory = MethodDataFetcherFactory(listOf())
        )

        val schema = provider.schema(
            """
            type Query {
                hello: String
                numbers: [Int]
                movies: [Movie]
                withError: String
                flow: [String]
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
            defaultSchema = schema,
            schemaProvider = provider,
            dataLoaderProvider = dgsDataLoaderProvider,
            contextBuilder = DefaultDgsReactiveGraphQLContextBuilder(Optional.empty()),
            instrumentation = SimpleInstrumentation.INSTANCE,
            queryExecutionStrategy = AsyncExecutionStrategy(),
            mutationExecutionStrategy = AsyncSerialExecutionStrategy(),
            idProvider = Optional.empty()
        )
    }

    @Test
    fun extractJsonWithMonoString() {
        val helloResult = dgsQueryExecutor.executeAndExtractJsonPath<String>(
            """
            {
                hello
            }
            """.trimIndent(),
            "data.hello"
        ).contextWrite(dummyContext())

        StepVerifier.create(helloResult).assertNext {
            assertThat(it).isEqualTo("hi!")
        }.verifyComplete()
        verify { stubContextConsumer.accept(match(comparingDummyContext())) }
    }

    @Test
    fun `extract json with flow`() {
        val flowResult = dgsQueryExecutor.executeAndExtractJsonPath<List<String>>(
            """
            {
                flow
            }
            """.trimIndent(),
            "data.flow"
        ).contextWrite(dummyContext())

        val step = StepVerifier.create(flowResult)
        step.assertNext {
            assertThat(it).isEqualTo(listOf("one", "two", "three"))
        }.verifyComplete()
    }

    @Test
    fun extractJsonWithFlux() {
        val numbers = dgsQueryExecutor.executeAndExtractJsonPath<List<Int>>(
            """
            {
                numbers
            }
            """.trimIndent(),
            "data.numbers"
        ).contextWrite(dummyContext())

        val step = StepVerifier.create(numbers)
        step.assertNext {
            assertThat(it).isEqualTo(listOf(0, 1, 2, 3, 4))
        }.verifyComplete()
        verify { stubContextConsumer.accept(match(comparingDummyContext())) }
    }

    @Test
    fun extractJsonWithMonoOfObjects() {
        val movies = dgsQueryExecutor.executeAndExtractJsonPath<List<Map<String, Any>>>(
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

    private fun dummyContext() = Context.of("some-key", "some context value")

    private fun comparingDummyContext() = { context: ContextView ->
        context.size() == 1 && context.get<String>("some-key") == "some context value"
    }

    private data class Movie(val title: String, val releaseDate: LocalDateTime?)
}
