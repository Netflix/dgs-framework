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

package com.netflix.graphql.dgs

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.validation.annotation.Validated

/**
 * Configuration properties for DGS.
 */
@ConstructorBinding
@ConfigurationProperties(prefix = "dgs.graphql")
@Validated
@Suppress("ConfigurationProperties")
data class DgsGraphQLConfigurationProperties(
    /** Path to the GraphQL endpoint without trailing slash. */
    @DefaultValue("/graphql") val path: String,
    @DefaultValue val graphiql: DgsGraphiQLConfigurationProperties,
    @DefaultValue val schemaJson: DgsSchemaJsonConfigurationProperties
) {
    /**
     * Configuration properties for GraphiQL.
     */
    data class DgsGraphiQLConfigurationProperties(
        /** Path to the GraphiQL endpoint without trailing slash. */
        @DefaultValue("/graphiql") val path: String
    )

    /**
     * Configuration properties for the schema-json endpoint.
     */
    data class DgsSchemaJsonConfigurationProperties(
        /** Path to the schema-json endpoint without trailing slash. */
        @DefaultValue("/schema.json") val path: String
    )
}
