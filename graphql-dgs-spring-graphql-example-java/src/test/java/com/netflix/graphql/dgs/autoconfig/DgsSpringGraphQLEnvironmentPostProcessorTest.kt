/*
 * Copyright 2023 Netflix, Inc.
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

import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.core.env.MapPropertySource
import org.springframework.mock.env.MockEnvironment

class DgsSpringGraphQLEnvironmentPostProcessorTest {
    @Test
    fun `Default schema location`() {

        val application = mockk<SpringApplication>()
        val env = MockEnvironment()

        DgsSpringGraphQLEnvironmentPostProcessor().postProcessEnvironment(env, application)

        assertThat(env.getProperty("spring.graphql.schema.locations")).isEqualTo("classpath*:schema/**/")
        assertThat(env.getProperty("spring.graphql.schema.fileExtensions")).isEqualTo("*.graphql*")
    }

    @Test
    fun `Explicitly set schema location`() {

        val application = mockk<SpringApplication>()
        val env = MockEnvironment()

        env.setProperty("dgs.graphql.schema-locations", "classpath*:dgs-schema/**/*.graphql*")

        DgsSpringGraphQLEnvironmentPostProcessor().postProcessEnvironment(env, application)

        assertThat(env.getProperty("spring.graphql.schema.locations")).isEqualTo("classpath*:dgs-schema/**/")
        assertThat(env.getProperty("spring.graphql.schema.fileExtensions")).isEqualTo("*.graphql*")
    }

    @Test
    fun `Explicitly set schema location to directory only`() {

        val application = mockk<SpringApplication>()
        val env = MockEnvironment()

        env.setProperty("dgs.graphql.schema-locations", "classpath*:schema/**/")

        DgsSpringGraphQLEnvironmentPostProcessor().postProcessEnvironment(env, application)

        assertThat(env.getProperty("spring.graphql.schema.locations")).isEqualTo("classpath*:schema/**/")
        assertThat(env.getProperty("spring.graphql.schema.fileExtensions")).isNull()
    }

    @Test
    fun `Explicitly set schema location to multiple`() {

        val application = mockk<SpringApplication>()
        val env = MockEnvironment()

        env.propertySources.addLast(
            MapPropertySource(
                "props",
                mapOf(
                    Pair(
                        "dgs.graphql.schema-locations",
                        listOf("classpath*:schema/**/", "classpath*:otherschemas/**/*graphql*")
                    )
                )
            )
        )

        DgsSpringGraphQLEnvironmentPostProcessor().postProcessEnvironment(env, application)

        assertThat(env.getProperty("spring.graphql.schema.locations")).isEqualTo("classpath*:schema/**/,classpath*:otherschemas/**/")
        assertThat(env.getProperty("spring.graphql.schema.fileExtensions")).isEqualTo("*graphql*")
    }
}