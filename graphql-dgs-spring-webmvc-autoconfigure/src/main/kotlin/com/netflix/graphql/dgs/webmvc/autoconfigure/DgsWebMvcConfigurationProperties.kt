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

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.validation.annotation.Validated
import javax.validation.Constraint
import javax.validation.Valid
import javax.validation.constraints.Pattern
import kotlin.reflect.KClass

/**
 * Configuration properties for DGS web controllers.
 */
@ConstructorBinding
@ConfigurationProperties(prefix = "dgs.graphql")
@Validated
@Suppress("ConfigurationProperties")
data class DgsWebMvcConfigurationProperties(
    /** Path to the GraphQL endpoint without trailing slash. */
    @DefaultValue("/graphql") @field:ValidPath val path: String,
    @DefaultValue @Valid val graphiql: DgsGraphiQLConfigurationProperties,
    @DefaultValue @Valid val schemaJson: DgsSchemaJsonConfigurationProperties
) {
    /**
     * Configuration properties for the schema-json endpoint.
     */
    data class DgsGraphiQLConfigurationProperties(
        /** Path to the GraphiQL endpoint without trailing slash. */
        @DefaultValue("/graphiql") @field:ValidPath val path: String
    )

    /**
     * Configuration properties for the schema-json endpoint.
     */
    data class DgsSchemaJsonConfigurationProperties(
        /** Path to the schema-json endpoint without trailing slash. */
        @DefaultValue("/schema.json") @field:ValidPath val path: String
    )

    /**
     * Convenience annotation to prevent having to repeat the @Pattern for every path field.
     */
    @kotlin.annotation.Target(AnnotationTarget.FIELD)
    @kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
    @Constraint(validatedBy = [])
    @Pattern(regexp = "^/.*[^/]\$", message = "path must start with '/' and not end with '/'")
    annotation class ValidPath(
        val message: String = "",
        val groups: Array<KClass<out Any>> = [],
        val payload: Array<KClass<out Any>> = []
    )
}
