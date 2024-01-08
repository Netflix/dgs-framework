/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.graphql.dgs.subscriptions.websockets.autoconfigure

import com.netflix.graphql.dgs.subscriptions.websockets.DgsWebSocketConfigurationProperties
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class DgsWebSocketConfigurationPropertiesValidationTest {

    private val context = ApplicationContextRunner().withConfiguration(
        AutoConfigurations.of(
            MockConfigPropsAutoConfiguration::class.java
        )
    )!!

    @Test
    fun webSocketValidCustomPath() {
        context
            .withPropertyValues("dgs.graphql.websocket.path: /pws")
            .run { ctx ->
                Assertions.assertThat(ctx).hasNotFailed()
            }
    }

    @Test
    fun websocketInvalidCustomPathEndsWithSlash() {
        context
            .withPropertyValues("dgs.graphql.websocket.path: /pws/")
            .run { ctx ->
                Assertions.assertThat(ctx).hasFailed()
                    .failure.rootCause().hasMessageContaining("dgs.graphql.websocket.path must start with '/' and not end with '/'")
            }
    }

    @Configuration
    @EnableConfigurationProperties(DgsWebSocketConfigurationProperties::class)
    open class MockConfigPropsAutoConfiguration
}
