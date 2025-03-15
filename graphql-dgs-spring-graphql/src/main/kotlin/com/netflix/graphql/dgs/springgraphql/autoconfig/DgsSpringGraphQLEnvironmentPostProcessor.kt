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
    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        val properties = mutableMapOf<String, Any>()
        resolveProperty(
            environment,
            properties,
            springGraphQlPropertyName = "spring.graphql.schema.introspection.enabled",
            dgsGraphQlPropertyName = "dgs.graphql.introspection.enabled",
            defaultValue = true,
            allowBothProperties = false,
        )
        resolveProperty(
            environment,
            properties,
            springGraphQlPropertyName = "spring.graphql.graphiql.enabled",
            dgsGraphQlPropertyName = "dgs.graphql.graphiql.enabled",
            defaultValue = true,
        )
        resolveProperty(
            environment,
            properties,
            springGraphQlPropertyName = "spring.graphql.graphiql.path",
            dgsGraphQlPropertyName = "dgs.graphql.graphiql.path",
            defaultValue = "/graphiql",
        )
        resolveProperty(
            environment,
            properties,
            springGraphQlPropertyName = "spring.graphql.path",
            dgsGraphQlPropertyName = "dgs.graphql.path",
            defaultValue = "/graphql",
        )
        resolveProperty(
            environment,
            properties,
            springGraphQlPropertyName = "spring.graphql.websocket.connection-init-timeout",
            dgsGraphQlPropertyName = "dgs.graphql.websocket.connection-init-timeout",
            defaultValue = "10s",
        )
        resolveProperty(
            environment,
            properties,
            springGraphQlPropertyName = "spring.graphql.websocket.connection-init-timeout",
            dgsGraphQlPropertyName = "dgs.graphql.websocket.connection-init-timeout",
            defaultValue = "10s",
        )
        resolveProperty(
            environment,
            properties,
            springGraphQlPropertyName = "spring.graphql.websocket.path",
            dgsGraphQlPropertyName = "dgs.graphql.websocket.path",
            defaultValue = "/graphql",
        )
        resolveProperty(
            environment,
            properties,
            springGraphQlPropertyName = "spring.threads.virtual.enabled",
            dgsGraphQlPropertyName = "dgs.graphql.virtualthreads.enabled",
            defaultValue = true,
        )

        environment.propertySources.addLast(
            MapPropertySource(
                "dgs-spring-graphql-defaults",
                properties,
            ),
        )
    }

    fun resolveProperty(
        environment: ConfigurableEnvironment,
        properties: MutableMap<String, Any>,
        springGraphQlPropertyName: String,
        dgsGraphQlPropertyName: String,
        defaultValue: Any,
        allowBothProperties: Boolean = true,
    ) {
        if (!allowBothProperties &&
            environment.getProperty(springGraphQlPropertyName) != null &&
            environment.getProperty(dgsGraphQlPropertyName) != null
        ) {
            throw RuntimeException(
                "Both properties `$springGraphQlPropertyName` and `$dgsGraphQlPropertyName` are explicitly set. Use `$dgsGraphQlPropertyName` only.",
            )
        } else if (environment.getProperty(dgsGraphQlPropertyName) != null) {
            properties[springGraphQlPropertyName] =
                environment.getProperty(
                    dgsGraphQlPropertyName,
                )!!
        } else {
            val propertyValue = environment[springGraphQlPropertyName] ?: defaultValue
            properties[springGraphQlPropertyName] = propertyValue
            properties[dgsGraphQlPropertyName] = propertyValue
        }
    }
}
