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

package com.netflix.graphql.dgs.autoconfig

import com.netflix.graphql.dgs.internal.DgsSchemaProvider.Companion.DEFAULT_SCHEMA_LOCATION
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

/**
 * Configuration properties for DGS framework.
 */
@ConfigurationProperties(prefix = DgsConfigurationProperties.PREFIX)
@Suppress("ConfigurationProperties")
data class DgsConfigurationProperties(
    /** Location of the GraphQL schema files. */
    @DefaultValue(DEFAULT_SCHEMA_LOCATION) val schemaLocations: List<String>,
    @DefaultValue("true") val schemaWiringValidationEnabled: Boolean,
    @DefaultValue("false") val enableEntityFetcherCustomScalarParsing: Boolean,
    val preparsedDocumentProvider: DgsPreparsedDocumentProviderConfigurationProperties = DgsPreparsedDocumentProviderConfigurationProperties()
) {
    data class DgsPreparsedDocumentProviderConfigurationProperties(
        val enabled: Boolean = false,
        val maximumCacheSize: Long = 2000,
        /** How long cache entries are valid for since creation, replacement or last access, specified with an ISO-8601 duration string. **/
        val cacheValidityDuration: String = "PT1H"
    )

    companion object {
        const val PREFIX: String = "dgs.graphql"
    }
}
