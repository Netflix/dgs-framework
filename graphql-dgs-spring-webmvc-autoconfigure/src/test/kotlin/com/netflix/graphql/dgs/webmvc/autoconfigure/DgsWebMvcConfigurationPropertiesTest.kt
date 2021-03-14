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

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySource
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import java.util.*

class DgsWebMvcConfigurationPropertiesTest {

    @Test
    fun graphQLPathDefault() {
        val properties = bind(Collections.emptyMap())
        Assertions.assertThat(properties.path).isEqualTo("/graphql")
    }

    @Test
    fun graphQLPathCustom() {
        val properties = bind("dgs.graphql.path", "/private/gql")
        Assertions.assertThat(properties.path).isEqualTo("/private/gql")
    }

    @Test
    fun graphiQLPathDefault() {
        val properties = bind(Collections.emptyMap())
        Assertions.assertThat(properties.graphiql.path).isEqualTo("/graphiql")
    }

    @Test
    fun graphiQLPathCustom() {
        val properties = bind("dgs.graphql.graphiql.path", "/private/giql")
        Assertions.assertThat(properties.graphiql.path).isEqualTo("/private/giql")
    }

    @Test
    fun schemaJsonPathDefault() {
        val properties = bind(Collections.emptyMap())
        Assertions.assertThat(properties.schemaJson.path).isEqualTo("/schema.json")
    }

    @Test
    fun schemaJsonPathCustom() {
        val properties = bind("dgs.graphql.schema-json.path", "/private/schema.json")
        Assertions.assertThat(properties.schemaJson.path).isEqualTo("/private/schema.json")
    }

    @Test
    fun allCustomPathsSpecified() {
        val propertyValues: MutableMap<String?, String?> = HashMap()
        propertyValues["dgs.graphql.path"] = "/private/gql"
        propertyValues["dgs.graphql.graphiql.path"] = "/private/giql"
        propertyValues["dgs.graphql.schema-json.path"] = "/private/sj"
        val properties = bind(propertyValues)
        Assertions.assertThat(properties.path).isEqualTo("/private/gql")
        Assertions.assertThat(properties.graphiql.path).isEqualTo("/private/giql")
        Assertions.assertThat(properties.schemaJson.path).isEqualTo("/private/sj")
    }

    private fun bind(name: String, value: String): DgsWebMvcConfigurationProperties {
        return bind(Collections.singletonMap(name, value))
    }

    private fun bind(map: Map<String?, String?>): DgsWebMvcConfigurationProperties {
        val source: ConfigurationPropertySource = MapConfigurationPropertySource(map)
        return Binder(source).bindOrCreate("dgs.graphql", DgsWebMvcConfigurationProperties::class.java)
    }
}
