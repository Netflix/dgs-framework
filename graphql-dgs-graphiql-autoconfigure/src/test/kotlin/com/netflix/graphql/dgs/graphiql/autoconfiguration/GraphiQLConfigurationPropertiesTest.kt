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
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySource
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import java.util.*

class GraphiQLConfigurationPropertiesTest {

    @Test
    fun graphiQLPathDefault() {
        val properties = bind(Collections.emptyMap())
        Assertions.assertThat(properties.path).isEqualTo("/graphiql")
    }

    @Test
    fun graphiQLPathCustom() {
        val properties = bind("dgs.graphql.graphiql.path", "/private/giql")
        Assertions.assertThat(properties.path).isEqualTo("/private/giql")
    }

    private fun bind(name: String, value: String): GraphiQLConfigurationProperties {
        return bind(Collections.singletonMap(name, value))
    }

    private fun bind(map: Map<String?, String?>): GraphiQLConfigurationProperties {
        val source: ConfigurationPropertySource = MapConfigurationPropertySource(map)
        return Binder(source).bindOrCreate("dgs.graphql.graphiql", GraphiQLConfigurationProperties::class.java)
    }
}
