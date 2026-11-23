/*
 * Copyright 2026 Netflix, Inc.
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

import com.netflix.graphql.dgs.DgsCodeRegistry
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataLoader
import com.netflix.graphql.dgs.DgsEnableDataFetcherInstrumentation
import com.netflix.graphql.dgs.DgsEntityFetcher
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.springgraphql.autoconfig.DgsSpringGraphQLAutoConfiguration
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MockClock
import io.micrometer.core.instrument.simple.SimpleConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.dataloader.BatchLoader
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.graphql.autoconfigure.GraphQlAutoConfiguration
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.graphql.execution.GraphQlSource
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS

internal class DynamicResolverMetricsTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    DgsSpringGraphQLAutoConfiguration::class.java,
                    GraphQlAutoConfiguration::class.java,
                    CompositeMeterRegistryAutoConfiguration::class.java,
                    MetricsAutoConfiguration::class.java,
                    DgsGraphQLMicrometerAutoConfiguration::class.java,
                ),
            ).withUserConfiguration(LocalTestConfiguration::class.java)

    @Test
    fun `programmatic root records standard metrics without changing annotated defaults`() {
        contextRunner.run { context ->
            val result =
                context
                    .getBean(GraphQlSource::class.java)
                    .graphQl()
                    .execute("query Dynamic { dynamic annotated annotatedAsync disabled }")
            assertThat(result.errors).isEmpty()
            assertThat(result.getData<Map<String, Any>>())
                .containsEntry("dynamic", "dynamic value")
                .containsEntry("annotated", "annotated")
                .containsEntry("annotatedAsync", "async")
                .containsEntry("disabled", "disabled")

            val registry = context.getBean(MeterRegistry::class.java)
            for (field in listOf("Query.annotated", "Query.annotatedAsync")) {
                val timer = registry.get("gql.resolver").tag("gql.field", field).timer()
                assertThat(timer.count()).isEqualTo(1)
            }
            assertThat(registry.find("gql.resolver").tag("gql.field", "Query.disabled").timer()).isNull()

            val provider = context.getBean(DgsSchemaProvider::class.java)
            assertThat(provider.isFieldTracingInstrumentationEnabled("Query.annotatedAsync")).isFalse()
            assertThat(provider.isFieldTracingInstrumentationEnabled("Query.disabled")).isFalse()
            assertThat(provider.isFieldMetricsInstrumentationEnabled("_entities.Entity")).isFalse()
            assertThat(provider.isFieldTracingInstrumentationEnabled("_entities.Entity")).isFalse()

            assertThat(registry.find("gql.resolver").tag("gql.field", "Query.dynamic").timer())
                .describedAs("resolver timer for a fetcher registered through @DgsCodeRegistry")
                .isNotNull()
            val timer = registry.get("gql.resolver").tag("gql.field", "Query.dynamic").timer()
            assertThat(timer.count()).isEqualTo(1)
            assertStandardTags(registry, "Query.dynamic")
            assertThat(provider.isFieldMetricsInstrumentationEnabled("Query.dynamic")).isTrue()
            assertThat(provider.isFieldTracingInstrumentationEnabled("Query.dynamic")).isTrue()
        }
    }

    @Test
    fun `programmatic DataLoader extension includes time waiting for its future`() {
        contextRunner.run { context ->
            val fetchers = context.getBean(DynamicFetchers::class.java)
            val registry = context.getBean(MeterRegistry::class.java)
            val clock = context.getBean(MockClock::class.java)
            val executor = context.getBean(DgsQueryExecutor::class.java)
            val execution = CompletableFuture.supplyAsync { executor.execute("query DynamicLoader { item { value extension } }") }
            try {
                assertThat(fetchers.batchStarted.get(5, SECONDS)).containsExactly("key")
                assertThat(execution).isNotDone()
                assertThat(registry.find("gql.resolver").tag("gql.field", "Item.extension").timer()).isNull()

                clock.addSeconds(2)
                fetchers.batchResult.complete(listOf("loaded value"))
                val result = execution.get(5, SECONDS)
                assertThat(result.errors).isEmpty()
                assertThat(result.getData<Map<String, Any>>())
                    .containsEntry("item", mapOf("value" to "key", "extension" to "loaded value"))

                // GraphQL and metric callbacks can complete in either order on the same future.
                await().atMost(Duration.ofSeconds(5)).untilAsserted {
                    val timer = registry.find("gql.resolver").tag("gql.field", "Item.extension").timer()
                    assertThat(timer).isNotNull()
                    assertThat(timer!!.count()).isEqualTo(1)
                    assertThat(timer.totalTime(SECONDS)).isEqualTo(2.0)
                }
                assertThat(registry.find("gql.resolver").tag("gql.field", "Item.value").timer()).isNull()
                assertStandardTags(registry, "Item.extension")
                assertThat(context.getBean(DgsSchemaProvider::class.java).isFieldTracingInstrumentationEnabled("Item.extension")).isTrue()
            } finally {
                fetchers.batchResult.complete(listOf("loaded value"))
                execution.get(5, SECONDS)
            }
        }
    }

    @Test
    fun `global resolver switch disables both programmatic and annotated metrics`() {
        contextRunner.withPropertyValues("management.metrics.dgs-graphql.resolver.enabled=false").run { context ->
            val result = context.getBean(GraphQlSource::class.java).graphQl().execute("{ dynamic annotated }")
            assertThat(result.errors).isEmpty()
            assertThat(result.getData<Map<String, Any>>())
                .containsEntry("dynamic", "dynamic value")
                .containsEntry("annotated", "annotated")
            val registry = context.getBean(MeterRegistry::class.java)
            assertThat(registry.find("gql.query").timer()).isNotNull()
            assertThat(registry.find("gql.resolver").timers()).isEmpty()
        }
    }

    private fun assertStandardTags(
        registry: MeterRegistry,
        field: String,
    ) {
        val query = registry.get("gql.query").timer()
        val resolver = registry.get("gql.resolver").tag("gql.field", field).timer()
        for (tag in listOf("gql.operation", "gql.operation.name", "gql.query.complexity", "gql.query.sig.hash", "gql.persistedQueryType")) {
            assertThat(query.id.getTag(tag)).describedAs("query tag $tag").isNotNull()
            assertThat(resolver.id.getTag(tag)).describedAs("resolver tag $tag").isEqualTo(query.id.getTag(tag))
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class LocalTestConfiguration {
        @Bean
        fun clock(): MockClock = MockClock()

        @Bean
        fun registry(clock: MockClock): MeterRegistry = SimpleMeterRegistry(SimpleConfig.DEFAULT, clock)

        @Bean
        fun fetchers(): DynamicFetchers = DynamicFetchers()
    }

    @DgsComponent
    class DynamicFetchers {
        val batchStarted = CompletableFuture<List<String>>()
        val batchResult = CompletableFuture<List<String>>()

        @DgsDataLoader(name = "dynamicLoader")
        val loader =
            BatchLoader<String, String> { keys ->
                batchStarted.complete(keys)
                batchResult
            }

        @DgsTypeDefinitionRegistry
        fun schema(): TypeDefinitionRegistry =
            SchemaParser().parse(
                """
                type Query { dynamic: String item: Item annotated: String annotatedAsync: String disabled: String }
                type Item { value: String extension: String }
                type Entity @key(fields: "id") { id: ID! }
                """.trimIndent(),
            )

        @DgsQuery
        fun annotated(): String = "annotated"

        @DgsQuery
        fun annotatedAsync(): CompletableFuture<String> = CompletableFuture.completedFuture("async")

        @DgsQuery
        @DgsEnableDataFetcherInstrumentation(false)
        fun disabled(): String = "disabled"

        @DgsEntityFetcher(name = "Entity")
        fun entity(representation: Map<String, Any>): Map<String, Any> = representation

        @DgsCodeRegistry
        fun registry(
            builder: GraphQLCodeRegistry.Builder,
            @Suppress("UNUSED_PARAMETER") registry: TypeDefinitionRegistry,
        ): GraphQLCodeRegistry.Builder =
            builder
                .dataFetcher(FieldCoordinates.coordinates("Query", "dynamic"), DataFetcher { "dynamic value" })
                .dataFetcher(FieldCoordinates.coordinates("Query", "item"), DataFetcher { mapOf("value" to "key") })
                .dataFetcher(
                    FieldCoordinates.coordinates("Item", "extension"),
                    DataFetcher { environment ->
                        environment.getDataLoader<String, String>("dynamicLoader")!!.load("key")
                    },
                )
    }
}
