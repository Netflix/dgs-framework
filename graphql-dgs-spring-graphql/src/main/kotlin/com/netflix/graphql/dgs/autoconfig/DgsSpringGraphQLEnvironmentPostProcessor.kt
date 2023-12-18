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

import com.netflix.graphql.dgs.exceptions.InvalidDgsConfigurationException
import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import kotlin.collections.MutableMap
import kotlin.collections.mutableMapOf
import kotlin.collections.set

class DgsSpringGraphQLEnvironmentPostProcessor : EnvironmentPostProcessor {
    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val properties = mutableMapOf<String, Any>()

        properties["spring.graphql.graphiql.enabled"] = environment.getProperty("dgs.graphql.graphiql.enabled") ?: true
        properties["spring.graphql.graphiql.path"] = environment.getProperty("dgs.graphql.graphiql.path") ?: "/graphiql"
        properties["spring.graphql.websocket.connection-init-timeout"] = environment.getProperty("dgs.graphql.websocket.connection-init-timeout") ?: "10s"

        environment.getProperty("dgs.graphql.websocket.path")?.let {
            properties["spring.graphql.websocket.path"] = environment.getProperty("dgs.graphql.websocket.path")!!
        }

        environment.propertySources.addLast(
            MapPropertySource(
                "dgs-spring-graphql-defaults",
                properties
            )
        )
    }

    private fun defaultSchemaLocation(environment: ConfigurableEnvironment, properties: MutableMap<String, Any>) {
        environment.getProperty("spring.graphql.schema.locations")?.let {
            throw InvalidDgsConfigurationException("Do not set spring.graphql.schema.locations, instead use dgs.graphql.schema-locations")
        }

        environment.getProperty("spring.graphql.schema.fileExtensions")?.let {
            throw InvalidDgsConfigurationException("Do not set spring.graphql.schema.fileExtensions, instead use dgs.graphql.schema-locations")
        }
    }
}
