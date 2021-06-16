/*
 * Copyright 2020 Netflix, Inc.
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

import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.autoconfig.testcomponents.CustomContextBuilderConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner

class DisabledIntrospectionTest {
    private val context =
        WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(DgsAutoConfiguration::class.java))
            .withPropertyValues("dgs.graphql.introspection-enabled=false")!!

    @Test
    fun disabledIntrospectionTest() {
        context.withUserConfiguration(CustomContextBuilderConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                val json = it.execute(
                    " query availableQueries {\n" +
                        "  __schema {\n" +
                        "    queryType {\n" +
                        "      fields {\n" +
                        "        name\n" +
                        "        description\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}"
                )
                assertThat(json.errors.size).isGreaterThan(0)
            }
        }
    }
}
