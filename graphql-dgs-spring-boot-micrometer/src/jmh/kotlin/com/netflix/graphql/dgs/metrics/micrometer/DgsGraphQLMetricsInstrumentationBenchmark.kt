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

package com.netflix.graphql.dgs.metrics.micrometer

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.method.DataFetchingEnvironmentArgumentResolver
import com.netflix.graphql.dgs.internal.method.FallbackEnvironmentArgumentResolver
import com.netflix.graphql.dgs.internal.method.InputArgumentResolver
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import graphql.ExecutionInput
import graphql.GraphQL
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.mock.env.MockEnvironment
import java.util.*
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(value = [ Mode.Throughput, Mode.AverageTime ])
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
open class DgsGraphQLMetricsInstrumentationBenchmark {
    private lateinit var applicationContext: AnnotationConfigApplicationContext

    private lateinit var graphql: GraphQL

    @Setup
    @BeforeEach
    open fun setup() {
        val mockEnvironment = MockEnvironment()

        applicationContext = AnnotationConfigApplicationContext()
        applicationContext.environment = mockEnvironment
        applicationContext.register(BenchmarkedDataFetcher::class.java)
        applicationContext.refresh()
        applicationContext.start()

        val provider =
            DgsSchemaProvider(
                applicationContext = applicationContext,
                federationResolver = Optional.empty(),
                existingTypeDefinitionRegistry = Optional.empty(),
                methodDataFetcherFactory =
                    MethodDataFetcherFactory(
                        listOf(
                            InputArgumentResolver(DefaultInputObjectMapper()),
                            DataFetchingEnvironmentArgumentResolver(),
                            FallbackEnvironmentArgumentResolver(DefaultInputObjectMapper()),
                        ),
                    ),
            )

        val simpleMeter = SimpleMeterRegistry()
        val properties = DgsGraphQLMetricsProperties()
        val metricsInstrumentation =
            DgsGraphQLMetricsInstrumentation(
                schemaProvider = provider,
                registrySupplier = { simpleMeter },
                tagsProvider = DgsGraphQLCollatedMetricsTagsProvider(),
                properties = properties,
                limitedTagMetricResolver = SpectatorLimitedTagMetricResolver(properties.tags),
            )

        graphql = GraphQL.newGraphQL(provider.schema(schema)).instrumentation(metricsInstrumentation).build()
    }

    @AfterEach
    @TearDown
    fun destroy() {
        applicationContext.stop()
    }

    @Benchmark
    @Test
    open fun helloGraphQLQuery() {
        val executionResult = graphql.execute(simpleHelloQuery)
        assertThat(executionResult).isNotNull
        assertThat(executionResult.errors).isEmpty()
        assertThat(executionResult.isDataPresent).isTrue
        val data = executionResult.getData<Map<String, *>>()
        assertThat(data).hasEntrySatisfying("hello") {
            assertThat(it).isInstanceOf(String::class.java).asString().isNotEmpty()
        }
    }

    @Benchmark
    @Test
    open fun numbers() {
        val executionResult = graphql.execute(numbersExecutionInput)
        assertThat(executionResult).isNotNull
        assertThat(executionResult.errors).isEmpty()
        assertThat(executionResult.isDataPresent).isTrue
        val data = executionResult.getData<Map<String, *>>()
        assertThat(data).hasEntrySatisfying("size") { assertThat(it).isNotNull }
    }

    companion object {
        @Language("GraphQL")
        private val schema =
            """
            type Query {
                hello(name: String): String
                size(list: [Int]): Int
            }
            """.trimIndent()

        @Language("GraphQL")
        private val simpleHelloQuery = """{ hello(name: "benchmark") }"""

        private val numbersExecutionInput: ExecutionInput =
            ExecutionInput
                .newExecutionInput("""query CalcSize(${"$"}numbers: [Int]) { size(list: ${"$"}numbers) }""")!!
                .variables(mapOf("numbers" to (0..200).toList()))
                .operationName("CalcSize")
                .build()
    }

    @DgsComponent
    open class BenchmarkedDataFetcher {
        @DgsQuery(field = "hello")
        fun hello(
            @InputArgument name: String,
        ): String = "Hello, $name"

        @DgsQuery(field = "size")
        fun size(
            @InputArgument list: List<Int>,
        ): Int = list.size
    }
}
