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

package com.netflix.graphql.dgs.metrics.micrometer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

internal class DgsGraphQLMetricsPropertiesTest {

    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TestConfiguration::class.java))

    @Test
    fun `Provides sensible defaults`() {
        contextRunner.run { ctx ->
            val props = ctx.getBean(DgsGraphQLMetricsProperties::class.java)

            assertThat(props).isNotNull
            assertThat(props.autotime.isEnabled).isTrue
            assertThat(props.autotime.percentiles).isNull()
            assertThat(props.autotime.isPercentilesHistogram).isFalse

            assertThat(props.tags).isNotNull
            assertThat(props.tags.limiter.kind).isEqualTo(DgsGraphQLMetricsProperties.CardinalityLimiterKind.FIRST)
            assertThat(props.tags.limiter.limit).isEqualTo(100)
            assertThat(props.tags.complexity.enabled).isTrue()

            assertThat(props.resolver.enabled).isTrue()
            assertThat(props.query.enabled).isTrue()
        }
    }

    @Test
    fun `Can override tag limiters`() {
        contextRunner
            .withPropertyValues(
                "management.metrics.dgs-graphql.tags.limiter.kind=FREQUENCY",
                "management.metrics.dgs-graphql.tags.limiter.limit=500"
            ).run { ctx ->
                val props = ctx.getBean(DgsGraphQLMetricsProperties::class.java)

                assertThat(props.tags).isNotNull
                assertThat(props.tags.limiter.kind).isEqualTo(DgsGraphQLMetricsProperties.CardinalityLimiterKind.FREQUENCY)
                assertThat(props.tags.limiter.limit).isEqualTo(500)
            }
    }

    @Test
    fun `Can disable complexity tag`() {
        contextRunner
            .withPropertyValues(
                "management.metrics.dgs-graphql.tags.complexity.enabled=false"
            ).run { ctx ->
                val props = ctx.getBean(DgsGraphQLMetricsProperties::class.java)

                assertThat(props.tags.complexity.enabled).isFalse()
            }
    }

    @Test
    fun `Can disable resolver metric`() {
        contextRunner
            .withPropertyValues(
                "management.metrics.dgs-graphql.resolver.enabled=false"
            ).run { ctx ->
                val props = ctx.getBean(DgsGraphQLMetricsProperties::class.java)

                assertThat(props.resolver.enabled).isFalse()
            }
    }

    @Test
    fun `Can disable query metric`() {
        contextRunner
            .withPropertyValues(
                "management.metrics.dgs-graphql.query.enabled=false"
            ).run { ctx ->
                val props = ctx.getBean(DgsGraphQLMetricsProperties::class.java)

                assertThat(props.query.enabled).isFalse()
            }
    }

    @Test
    fun `can override autotime configuration`() {
        contextRunner.withPropertyValues(
            "management.metrics.dgs-graphql.autotime.percentiles-histogram=true",
            "management.metrics.dgs-graphql.autotime.percentiles=0.50,0.95,0.99"
        )
            .run { ctx ->
                val props = ctx.getBean(DgsGraphQLMetricsProperties::class.java)
                assertThat(props.autotime.isPercentilesHistogram).isTrue()
                assertThat(props.autotime.percentiles).isEqualTo(doubleArrayOf(0.50, 0.95, 0.99))
            }

        contextRunner.withPropertyValues(
            "management.metrics.dgs-graphql.autotime.enabled=false"
        )
            .run { ctx ->
                val props = ctx.getBean(DgsGraphQLMetricsProperties::class.java)
                assertThat(props.autotime.isEnabled).isFalse()
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DgsGraphQLMetricsProperties::class)
    open class TestConfiguration
}
