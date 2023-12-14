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

import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

class DgsSpringGraphQLEnvironmentPostProcessor : EnvironmentPostProcessor {
    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val dgsLocation =
            environment.getProperty("dgs.graphql.schema-locations") ?: DgsSchemaProvider.DEFAULT_SCHEMA_LOCATION

        val schemaDirs = dgsLocation.split(",").map {
            it.substringBeforeLast("/") + "/"
        }

        val fileExtensions = dgsLocation.split(",").mapNotNull {
            val extension = it.substringAfterLast("/")
            extension.ifBlank {
                null
            }
        }

        val properties = if(fileExtensions.isNotEmpty()) {
            mapOf(
                Pair("spring.graphql.schema.locations", schemaDirs),
                Pair("spring.graphql.schema.fileExtensions", fileExtensions)
            )
        } else {
            mapOf(
                Pair("spring.graphql.schema.locations", schemaDirs)
            )
        }

        environment.propertySources.addLast(
            MapPropertySource(
                "dgs-spring-graphql-defaults",
                properties
            )
        )
    }
}