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

package com.netflix.graphql.dgs.webmvc.autoconfigure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class DgsWebMvcConfigurationPropertiesValidationTest {

    private val context = ApplicationContextRunner().withConfiguration(
        AutoConfigurations.of(
            MockConfigPropsAutoConfiguration::class.java
        )
    )!!

    @Test
    fun graphqlControllerInvalidCustomPathEndsWithSlash() {
        context
            .withPropertyValues("dgs.graphql.path: /fooql/")
            .run { ctx ->
                assertThat(ctx).hasFailed().failure.rootCause().hasMessageContaining("dgs.graphql.path must start with '/' and not end with '/'")
            }
    }

    @Test
    fun graphqlControllerInvalidCustomPathDoesNotStartWithSlash() {
        context
            .withPropertyValues("dgs.graphql.path: fooql")
            .run { ctx ->
                assertThat(ctx).hasFailed().failure.rootCause().hasMessageContaining("dgs.graphql.path must start with '/' and not end with '/'")
            }
    }

    @Test
    fun graphqlControllerValidCustomPath() {
        context
            .withPropertyValues("dgs.graphql.path: /fooql")
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
            }
    }

    @Test
    fun graphiqlControllerInvalidCustomPathEndsWithSlash() {
        context
            .withPropertyValues("dgs.graphql.graphiql.path: /fooql/")
            .run { ctx ->
                assertThat(ctx).hasFailed().failure.rootCause().hasMessageContaining("dgs.graphql.graphiql.path must start with '/' and not end with '/'")
            }
    }

    @Test
    fun graphiqlControllerInvalidCustomPathDoesNotStartWithSlash() {
        context
            .withPropertyValues("dgs.graphql.graphiql.path: fooql")
            .run { ctx ->
                assertThat(ctx).hasFailed().failure.rootCause().hasMessageContaining("dgs.graphql.graphiql.path must start with '/' and not end with '/'")
            }
    }

    @Test
    fun graphiqlControllerValidCustomPath() {
        context
            .withPropertyValues("dgs.graphql.graphiql.path: /fooql")
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
            }
    }

    @Test
    fun schemaJsonControllerInvalidCustomPathEndsWithSlash() {
        context
            .withPropertyValues("dgs.graphql.schema-json.path: /fooql/")
            .run { ctx ->
                assertThat(ctx).hasFailed().failure.rootCause().hasMessageContaining("dgs.graphql.schema-json.path must start with '/' and not end with '/'")
            }
    }

    @Test
    fun schemaJsonControllerInvalidCustomPathDoesNotStartWithSlash() {
        context
            .withPropertyValues("dgs.graphql.schema-json.path: fooql")
            .run { ctx ->
                assertThat(ctx).hasFailed().failure.rootCause().hasMessageContaining("dgs.graphql.schema-json.path must start with '/' and not end with '/'")
            }
    }

    @Test
    fun schemaJsonControllerValidCustomPath() {
        context
            .withPropertyValues("dgs.graphql.schema-json.path: /fooql")
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
            }
    }

    @Test
    fun defaultsAreValid() {
        context
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
            }
    }

    @Configuration
    @EnableConfigurationProperties(DgsWebMvcConfigurationProperties::class)
    open class MockConfigPropsAutoConfiguration
}
