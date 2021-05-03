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

package com.netflix.graphql.dgs.autoconfig

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

internal class DgsExtendedScalarsAutoConfigurationTests {

    private val context =
        ApplicationContextRunner().withConfiguration(
            AutoConfigurations.of(DgsExtendedScalarsAutoConfiguration::class.java)
        )

    @Test
    fun `Scalars Autoconfiguration elements are available by default`() {
        context.run { context ->
            assertThat(context)
                .hasSingleBean(DgsExtendedScalarsAutoConfiguration::class.java)
                .hasSingleBean(DgsExtendedScalarsAutoConfiguration.CharsExtendedScalarsAutoConfiguration::class.java)
                .hasSingleBean(DgsExtendedScalarsAutoConfiguration.NumbersExtendedScalarsAutoConfiguration::class.java)
                .hasSingleBean(DgsExtendedScalarsAutoConfiguration.ObjectsExtendedScalarsAutoConfiguration::class.java)
                .hasSingleBean(DgsExtendedScalarsAutoConfiguration.TimeExtendedScalarsAutoConfiguration::class.java)
        }
    }

    @Test
    fun `Scalars Autoconfiguration can be disabled`() {
        context.withPropertyValues(
            "dgs.graphql.extensions.scalars.enabled=false"
        ).run { context ->
            assertThat(context)
                .doesNotHaveBean(DgsExtendedScalarsAutoConfiguration::class.java)
        }
    }

    @Test
    fun `Date & Time scalars can be disabled`() {
        context.withPropertyValues(
            "dgs.graphql.extensions.scalars.time-dates.enabled=false"
        ).run { context ->
            assertThat(context)
                .hasSingleBean(DgsExtendedScalarsAutoConfiguration::class.java)
            assertThat(context)
                .doesNotHaveBean(DgsExtendedScalarsAutoConfiguration.TimeExtendedScalarsAutoConfiguration::class.java)
        }
    }

    @Test
    fun `Object, Json, Url, and Locale scalars can be disabled`() {
        context.withPropertyValues(
            "dgs.graphql.extensions.scalars.objects.enabled=false"
        ).run { context ->
            assertThat(context)
                .hasSingleBean(DgsExtendedScalarsAutoConfiguration::class.java)
            assertThat(context)
                .doesNotHaveBean(DgsExtendedScalarsAutoConfiguration.ObjectsExtendedScalarsAutoConfiguration::class.java)
        }
    }

    @Test
    fun `Number scalars can be disabled`() {
        context.withPropertyValues(
            "dgs.graphql.extensions.scalars.numbers.enabled=false"
        ).run { context ->
            assertThat(context)
                .hasSingleBean(DgsExtendedScalarsAutoConfiguration::class.java)
            assertThat(context)
                .doesNotHaveBean(DgsExtendedScalarsAutoConfiguration.NumbersExtendedScalarsAutoConfiguration::class.java)
        }
    }

    @Test
    fun `Chars scalars can be disabled`() {
        context.withPropertyValues(
            "dgs.graphql.extensions.scalars.chars.enabled=false"
        ).run { context ->
            assertThat(context)
                .hasSingleBean(DgsExtendedScalarsAutoConfiguration::class.java)
            assertThat(context)
                .doesNotHaveBean(DgsExtendedScalarsAutoConfiguration.CharsExtendedScalarsAutoConfiguration::class.java)
        }
    }
}
