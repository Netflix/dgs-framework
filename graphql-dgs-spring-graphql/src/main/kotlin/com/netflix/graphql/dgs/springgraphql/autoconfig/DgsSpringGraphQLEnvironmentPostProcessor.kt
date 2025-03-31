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

package com.netflix.graphql.dgs.springgraphql.autoconfig

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.get

class DgsSpringGraphQLEnvironmentPostProcessor : EnvironmentPostProcessor {
    companion object {
        private const val SPRING_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED = "spring.graphql.schema.introspection.enabled"
        private const val DGS_GRAPHQL_INTROSPECTION_ENABLED = "dgs.graphql.introspection.enabled"
    }

    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        val properties = mutableMapOf<String, Any>()

        if (environment.getProperty(SPRING_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED) != null &&
            environment.getProperty(DGS_GRAPHQL_INTROSPECTION_ENABLED) != null
        ) {
            throw RuntimeException(
                "Both properties `$SPRING_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED` and `$DGS_GRAPHQL_INTROSPECTION_ENABLED` are explicitly set. Use `$DGS_GRAPHQL_INTROSPECTION_ENABLED` only",
            )
        } else if (environment.getProperty(DGS_GRAPHQL_INTROSPECTION_ENABLED) != null) {
            properties[SPRING_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED] = environment.getProperty(
                DGS_GRAPHQL_INTROSPECTION_ENABLED,
            ) ?: true
        } else {
            properties[SPRING_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED] =
                environment[SPRING_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED] ?: true
        }

        properties["spring.graphql.graphiql.enabled"] = environment.getProperty("dgs.graphql.graphiql.enabled") ?: true
        properties["spring.graphql.graphiql.path"] = environment.getProperty("dgs.graphql.graphiql.path") ?: "/graphiql"
        properties["spring.graphql.path"] = environment.getProperty("dgs.graphql.path") ?: "/graphql"
        properties["spring.graphql.websocket.connection-init-timeout"] =
            environment.getProperty("dgs.graphql.websocket.connection-init-timeout") ?: "10s"

        environment.getProperty("dgs.graphql.websocket.path")?.let { websocketPath ->
            properties["spring.graphql.websocket.path"] = websocketPath
        }

        if (environment.getProperty("dgs.graphql.virtualthreads.enabled") == null &&
            environment.getProperty("spring.threads.virtual.enabled") == "true"
        ) {
            properties["dgs.graphql.virtualthreads.enabled"] = true
        }

        environment.propertySources.addLast(
            MapPropertySource(
                "dgs-spring-graphql-defaults",
                properties,
            ),
        )
    }
}
