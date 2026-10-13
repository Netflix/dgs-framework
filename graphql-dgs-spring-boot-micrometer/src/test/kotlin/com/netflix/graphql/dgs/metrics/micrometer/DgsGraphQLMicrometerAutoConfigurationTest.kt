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
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.metrics.micrometer.dataloader.DgsDataLoaderInstrumentationProvider
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsGraphQLMetricsTagsProvider
import com.netflix.graphql.dgs.metrics.micrometer.tagging.SimpleGqlOutcomeTagCustomizer
import com.netflix.graphql.dgs.metrics.micrometer.utils.QuerySignatureRepository
import com.netflix.graphql.dgs.springgraphql.autoconfig.DgsSpringGraphQLAutoConfiguration
import graphql.Scalars
import graphql.execution.ExecutionStepInfo
import graphql.execution.MergedField
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.language.Field
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.annotation.UserConfigurations
import org.springframework.boot.graphql.autoconfigure.GraphQlAutoConfiguration
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean

internal class DgsGraphQLMicrometerAutoConfigurationTest {
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
            ).withConfiguration(
                UserConfigurations.of(LocalTestConfiguration::class.java),
            )

    @Test
    fun `Default settings`() {
        contextRunner.run { ctx ->

            assertThat(ctx)
                .hasSingleBean(DgsGraphQLMetricsInstrumentation::class.java)
                .hasSingleBean(DgsDataLoaderInstrumentationProvider::class.java)
                .hasSingleBean(LimitedTagMetricResolver::class.java)
                .hasSingleBean(SimpleGqlOutcomeTagCustomizer::class.java)

            assertThat(ctx)
                .hasSingleBean(DgsMeterRegistrySupplier::class.java)
                .getBean(DgsMeterRegistrySupplier::class.java)
                .extracting { assertThat(it?.get()).isNotNull }

            assertThat(ctx)
                .hasSingleBean(DgsGraphQLMetricsTagsProvider::class.java)
                .getBean(DgsGraphQLMetricsTagsProvider::class.java)
                .extracting {
                    assertThat(it).isExactlyInstanceOf(DgsGraphQLCollatedMetricsTagsProvider::class.java)
                }

            assertThat(ctx)
                .hasSingleBean(DgsGraphQLMicrometerAutoConfiguration.QuerySignatureRepositoryConfiguration::class.java)
                .hasSingleBean(QuerySignatureRepository::class.java)

            assertThat(ctx)
                .hasSingleBean(DgsGraphQLMicrometerAutoConfiguration.QuerySignatureRepositoryConfiguration::class.java)
            assertThat(ctx)
                .hasSingleBean(QuerySignatureRepository::class.java)
        }
    }

    @Test
    fun `Beans can be disabled`() {
        contextRunner
            .withPropertyValues("management.metrics.dgs-graphql.enabled=false")
            .run { ctx ->
                assertThat(ctx).doesNotHaveBean(DgsGraphQLMicrometerAutoConfiguration::class.java)
            }

        contextRunner
            .withPropertyValues("management.metrics.dgs-graphql.instrumentation.enabled=false")
            .run { ctx ->
                assertThat(ctx)
                    .doesNotHaveBean(DgsGraphQLMetricsInstrumentation::class.java)

                assertThat(ctx)
                    .hasSingleBean(DgsDataLoaderInstrumentationProvider::class.java)
                    .hasSingleBean(SimpleGqlOutcomeTagCustomizer::class.java)
            }

        contextRunner
            .withPropertyValues("management.metrics.dgs-graphql.data-loader-instrumentation.enabled=false")
            .run { ctx ->
                assertThat(ctx)
                    .doesNotHaveBean(DgsDataLoaderInstrumentationProvider::class.java)

                assertThat(ctx)
                    .hasSingleBean(DgsGraphQLMetricsInstrumentation::class.java)
                    .hasSingleBean(SimpleGqlOutcomeTagCustomizer::class.java)
            }

        contextRunner
            .withPropertyValues("management.metrics.dgs-graphql.query-signature.enabled=false")
            .run { ctx ->
                assertThat(ctx)
                    .doesNotHaveBean(DgsGraphQLMicrometerAutoConfiguration.QuerySignatureRepositoryConfiguration::class.java)
                assertThat(ctx)
                    .doesNotHaveBean(QuerySignatureRepository::class.java)
            }
    }

    @Test
    fun `uses parent type for field metric coordinate`() {
        val queryType =
            GraphQLObjectType
                .newObject()
                .name("Query")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("ping").type(Scalars.GraphQLString))
                .build()
        val parent = mock(ExecutionStepInfo::class.java)
        `when`(parent.type).thenReturn(queryType)

        assertThat(DgsGraphQLMetricsInstrumentation.TagUtils.resolveDataFetcherTagValue(parameters(stepInfo(parent))))
            .isEqualTo("Query.ping")
    }

    @Test
    fun `uses unknown when parent type for field metric is missing`() {
        val parent = mock(ExecutionStepInfo::class.java)

        assertThat(DgsGraphQLMetricsInstrumentation.TagUtils.resolveDataFetcherTagValue(parameters(stepInfo(parent))))
            .isEqualTo(DgsGraphQLMetricsInstrumentation.TagUtils.TAG_VALUE_UNKNOWN)
    }

    private fun stepInfo(parent: ExecutionStepInfo): ExecutionStepInfo =
        mock(ExecutionStepInfo::class.java).also { step ->
            `when`(step.parent).thenReturn(parent)
            `when`(step.field).thenReturn(MergedField.newMergedField(Field.newField("ping").build()).build())
        }

    private fun parameters(step: ExecutionStepInfo): InstrumentationFieldFetchParameters =
        mock(InstrumentationFieldFetchParameters::class.java).also { parameters ->
            `when`(parameters.executionStepInfo).thenReturn(step)
        }

    @TestConfiguration(proxyBeanMethods = false)
    open class LocalTestConfiguration {
        @Bean
        open fun exampleImplementation(): ExampleImplementation = ExampleImplementation()
    }

    @DgsComponent
    open class ExampleImplementation {
        @DgsTypeDefinitionRegistry
        fun typeDefinitionRegistry(): TypeDefinitionRegistry = SchemaParser().parse("type Query{ }")
    }
}
