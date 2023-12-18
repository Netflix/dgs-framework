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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.mock.env.MockEnvironment

class DgsSpringGraphQLEnvironmentPostProcessorTest {
    val application = mockk<SpringApplication>()
    lateinit var env: MockEnvironment

    @BeforeEach
    fun setup() {
        env = MockEnvironment()
    }

    @Test
    fun `Default for graphiql-enabled`() {
        DgsSpringGraphQLEnvironmentPostProcessor().postProcessEnvironment(env, application)

        assertThat(env.getProperty("spring.graphql.graphiql.enabled")).isEqualTo("true")
    }

    @Test
    fun `DGS setting should propagate to spring graphql for graphiql-enabled`() {
        env.setProperty("dgs.graphql.graphiql.enabled", "false")

        DgsSpringGraphQLEnvironmentPostProcessor().postProcessEnvironment(env, application)

        assertThat(env.getProperty("spring.graphql.graphiql.enabled")).isEqualTo("false")
    }

    @Test
    fun `Default for graphiql-path`() {
        DgsSpringGraphQLEnvironmentPostProcessor().postProcessEnvironment(env, application)

        assertThat(env.getProperty("spring.graphql.graphiql.path")).isEqualTo("/graphiql")
    }

    @Test
    fun `DGS setting should propagate to spring graphql for graphiql-path`() {
        env.setProperty("dgs.graphql.graphiql.path", "/somepath")

        DgsSpringGraphQLEnvironmentPostProcessor().postProcessEnvironment(env, application)

        assertThat(env.getProperty("spring.graphql.graphiql.path")).isEqualTo("/somepath")
    }

    @Test
    fun `Default for websocket-connection-timeout`() {
        DgsSpringGraphQLEnvironmentPostProcessor().postProcessEnvironment(env, application)

        assertThat(env.getProperty("spring.graphql.websocket.connection-init-timeout")).isEqualTo("10s")
    }

    @Test
    fun `DGS setting should propagate to spring graphql for websocket-connection-timeout`() {
        env.setProperty("dgs.graphql.websocket.connection-init-timeout", "30s")

        DgsSpringGraphQLEnvironmentPostProcessor().postProcessEnvironment(env, application)

        assertThat(env.getProperty("spring.graphql.websocket.connection-init-timeout")).isEqualTo("30s")
    }
}
