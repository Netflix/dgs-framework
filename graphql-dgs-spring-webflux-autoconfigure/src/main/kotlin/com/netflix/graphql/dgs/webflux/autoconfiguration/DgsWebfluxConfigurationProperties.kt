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

package com.netflix.graphql.dgs.webflux.autoconfiguration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.bind.DefaultValue
import javax.annotation.PostConstruct

@ConstructorBinding
@ConfigurationProperties(prefix = "dgs.graphql")
@Suppress("ConfigurationProperties")
class DgsWebfluxConfigurationProperties(
    @DefaultValue("/graphql") val path: String,

    @DefaultValue
    val schemaJson: DgsSchemaJsonConfigurationProperties
) {
    /**
     * Configuration properties for the schema-json endpoint.
     */
    data class DgsSchemaJsonConfigurationProperties(
        /** Path to the schema-json endpoint without trailing slash. */
        @DefaultValue("/schema.json") val path: String
    )

    @PostConstruct
    fun validatePaths() {
        validatePath(this.path, "dgs.graphql.path")
        validatePath(this.schemaJson.path, "dgs.graphql.schema-json.path")
    }

    private fun validatePath(path: String, pathProperty: String) {
        if (!path.startsWith("/") || path.endsWith("/")) {
            throw IllegalArgumentException("$pathProperty must start with '/' and not end with '/' but was '$path'")
        }
    }
}
