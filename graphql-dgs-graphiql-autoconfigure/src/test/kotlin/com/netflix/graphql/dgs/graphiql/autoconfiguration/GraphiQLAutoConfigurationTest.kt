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

package com.netflix.graphql.dgs.graphiql.autoconfiguration

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner

class GraphiQLAutoConfigurationTest {

    private val context = WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(GraphiQLAutoConfiguration::class.java))!!

    @Test
    fun graphiqlAvailableWhenEnabledPropertyNotSpecified() {
        context.run { ctx ->
            Assertions.assertThat(ctx).hasSingleBean(GraphiQLConfigurer::class.java)
        }
    }

    @Test
    fun graphiqlAvailableWhenEnabledPropertySetToTrue() {
        context.withPropertyValues("dgs.graphql.graphiql.enabled: true").run { ctx ->
            Assertions.assertThat(ctx).hasSingleBean(GraphiQLConfigurer::class.java)
        }
    }

    @Test
    fun graphiqlNotAvailableWhenEnabledPropertySetToFalse() {
        context.withPropertyValues("dgs.graphql.graphiql.enabled: false").run { ctx ->
            Assertions.assertThat(ctx).doesNotHaveBean(GraphiQLConfigurer::class.java)
        }
    }
}
