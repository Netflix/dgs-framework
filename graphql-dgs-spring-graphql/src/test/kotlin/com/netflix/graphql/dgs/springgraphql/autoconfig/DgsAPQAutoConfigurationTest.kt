/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.graphql.dgs.springgraphql.autoconfig

import com.netflix.graphql.dgs.apq.DgsAPQSupportAutoConfiguration
import graphql.execution.preparsed.persisted.PersistedQueryCache
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class DgsAPQAutoConfigurationTest {
    private val autoConfigurations =
        AutoConfigurations.of(
            DgsSpringGraphQLAutoConfiguration::class.java,
            DgsAPQSupportAutoConfiguration::class.java,
            GraphQlAutoConfiguration::class.java,
        )

    @Test
    fun shouldContributeAPQBeans() {
        val contextRunner =
            ApplicationContextRunner()
                .withConfiguration(autoConfigurations)
                .withPropertyValues("dgs.graphql.apq.enabled=true")

        contextRunner.run { context ->
            assertThat(context)
                .hasBean("apqSourceBuilderCustomizer")
                .hasBean("sourceBuilderCustomizer")
                .hasSingleBean(PersistedQueryCache::class.java)
        }
    }

    @Test
    fun shouldNotContributeAPQBeans() {
        val contextRunner =
            ApplicationContextRunner()
                .withConfiguration(autoConfigurations)

        contextRunner.run { context ->
            assertThat(context)
                .doesNotHaveBean("apqSourceBuilderCustomizer")
                .hasBean("sourceBuilderCustomizer")
                .doesNotHaveBean(PersistedQueryCache::class.java)
        }
    }
}
