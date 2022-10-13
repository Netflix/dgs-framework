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

import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.internal.DataFetcherResultProcessor
import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.method.ContinuationArgumentResolver
import com.netflix.graphql.dgs.internal.method.FallbackEnvironmentArgumentResolver
import com.netflix.graphql.dgs.internal.method.InputArgumentResolver
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import graphql.ExecutionInput
import graphql.GraphQL
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.ReactorContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import reactor.core.publisher.Mono
import reactor.util.context.Context
import reactor.util.context.ContextView
import java.util.*
import java.util.function.Consumer
import kotlin.system.measureTimeMillis

class CoroutineDataFetcherTest {

    private val context = GenericApplicationContext()

    private val schemaProvider by lazy {
        DgsSchemaProvider(
            applicationContext = context,
            federationResolver = Optional.empty(),
            existingTypeDefinitionRegistry = Optional.empty(),
            methodDataFetcherFactory = MethodDataFetcherFactory(
                argumentResolvers = listOf(
                    InputArgumentResolver(DefaultInputObjectMapper()),
                    ContinuationArgumentResolver(),
                    FallbackEnvironmentArgumentResolver(DefaultInputObjectMapper())
                )
            ),
            dataFetcherResultProcessors = listOf(stubDataFetcherResultProcessor)
        )
    }

    private val stubDataFetcherResultProcessor = object : DataFetcherResultProcessor {
        override fun supportsType(originalResult: Any): Boolean = originalResult is Mono<*>
        override fun process(originalResult: Any, dfe: DgsDataFetchingEnvironment): Any =
            (originalResult as Mono<*>).contextWrite(Context.of("some-key", "some context value")).toFuture()
    }

    @Test
    fun `Suspend functions should be supported as datafetchers`() {
        val stubContextConsumer = mockk<Consumer<ContextView?>>()
        every { stubContextConsumer.accept(any()) } just Runs

        @DgsComponent
        class Fetcher {
            @DgsQuery
            suspend fun concurrent(@InputArgument from: Int, to: Int): Int = coroutineScope {
                var sum = 0
                repeat(from.rangeTo(to).count()) {
                    sum++
                    // Forcing a delay to demonstrate concurrency
                    delay(50)
                }
                // Capture ReactorContext from coroutine context
                stubContextConsumer.accept(coroutineContext[ReactorContext]?.context?.readOnly())
                sum
            }
        }

        context.beanFactory.registerSingleton("concurrentFetcher", Fetcher())
        context.refresh()

        val schema = schemaProvider.schema(
            """
            type Query {
                concurrent(from: Int, to: Int): Int
            }           
            """.trimIndent()
        )

        val build = GraphQL.newGraphQL(schema).build()

        val context = DgsContext(
            null,
            null
        )

        val concurrentTime = measureTimeMillis {
            val executionResult = build.execute(
                ExecutionInput.newExecutionInput().graphQLContext(context).query(
                    """
            {
                first: concurrent(from: 1, to: 10)
                second: concurrent(from: 2, to: 10)               
                third: concurrent(from: 3, to: 10)               
                fourth: concurrent(from: 4, to: 10)               
            }
                    """.trimIndent()
                ).build()
            )

            assertThat(executionResult.getData<Map<String, Int>>())
                .containsExactlyInAnyOrderEntriesOf(
                    mapOf(
                        "first" to 10,
                        "second" to 9,
                        "third" to 8,
                        "fourth" to 7
                    )
                )
        }

        val singleTime = measureTimeMillis {
            val executionResult = build.execute(
                ExecutionInput.newExecutionInput().graphQLContext(context).query(
                    """
            {
                first: concurrent(from: 1, to: 10)
            }
                    """.trimIndent()
                ).build()
            )

            assertThat(executionResult.getData<Map<String, Int>>())
                .containsExactlyInAnyOrderEntriesOf(mapOf("first" to 10))
        }

        assertThat(concurrentTime).isCloseTo(singleTime, Percentage.withPercentage(200.0))
        verify {
            val expectedContext: (ContextView) -> Boolean =
                { it.size() == 1 && it.get<String>("some-key") == "some context value" }
            stubContextConsumer.accept(match(expectedContext))
        }
    }

    @Test
    fun `Suspend functions with no arguments should be supported`() {
        @DgsComponent
        class Fetcher {
            @DgsQuery
            suspend fun concurrent(): Int = coroutineScope {
                42
            }
        }

        context.beanFactory.registerSingleton("concurrentFetcher", Fetcher())
        context.refresh()

        val schema = schemaProvider.schema(
            """
            type Query {
                concurrent: Int
            }           
            """.trimIndent()
        )

        val build = GraphQL.newGraphQL(schema).build()

        val context = DgsContext(
            null,
            null
        )

        val executionResult = build.execute(
            ExecutionInput.newExecutionInput().graphQLContext(context).query(
                "{ concurrent }"
            ).build()
        )

        assertThat(executionResult.isDataPresent).isTrue()
        assertThat(executionResult.getData<Map<String, Int>>()["concurrent"]).isEqualTo(42)
    }

    @Test
    fun `Throw the cause of InvocationTargetException from CoroutineDataFetcher`() {
        class CustomException(message: String?) : Exception(message)

        @DgsComponent
        class Fetcher {
            @DgsQuery
            suspend fun exceptionWithMessage(@InputArgument message: String?): Nothing = coroutineScope {
                throw CustomException(message)
            }
        }

        context.beanFactory.registerSingleton("exceptionWithMessageFetcher", Fetcher())
        context.refresh()

        val schema = schemaProvider.schema(
            """
            type Query {
                exceptionWithMessage(message: String): Int
            }           
            """.trimIndent()
        )
        val build = GraphQL.newGraphQL(schema).build()

        val context = DgsContext(
            null,
            null
        )

        val executionResult = build.execute(
            ExecutionInput.newExecutionInput().graphQLContext(context).query(
                """
                {
                    result: exceptionWithMessage(message: "Exception from coroutine")        
                }
                """.trimIndent()
            ).build()
        )

        assertThat(executionResult.errors.size).isEqualTo(1)
        assertThat(executionResult.errors[0].path).isEqualTo(listOf("result"))
        assertThat(executionResult.errors[0].message).isEqualTo("Exception while fetching data (/result) : Exception from coroutine")
    }
}
