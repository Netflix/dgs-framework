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
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration
import javax.annotation.PostConstruct

@ConstructorBinding
@ConfigurationProperties(prefix = "dgs.graphql")
@Suppress("ConfigurationProperties")
class DgsWebfluxConfigurationProperties(
    /** Websocket configuration. */
    @NestedConfigurationProperty var websocket: DgsWebsocketConfigurationProperties = DgsWebsocketConfigurationProperties(
        DEFAULT_CONNECTION_INIT_TIMEOUT_DURATION
    ),
    /** Path to the endpoint that will serve GraphQL requests. */
    @DefaultValue("/graphql") var path: String = "/graphql",
    @NestedConfigurationProperty var graphiql: DgsGraphiQLConfigurationProperties = DgsGraphiQLConfigurationProperties(),
    @NestedConfigurationProperty var schemaJson: DgsSchemaJsonConfigurationProperties = DgsSchemaJsonConfigurationProperties()
) {
    /**
     *  Configuration properties for websockets.
     */
    data class DgsWebsocketConfigurationProperties(
        /** Connection Initialization timeout for graphql-transport-ws. */
        @DefaultValue(DEFAULT_CONNECTION_INIT_TIMEOUT) var connectionInitTimeout: Duration
    )

    /**
     * Configuration properties for the GraphiQL endpoint.
     */
    data class DgsGraphiQLConfigurationProperties(
        /** Path to the GraphiQL endpoint without trailing slash. */
        @DefaultValue("/graphiql") var path: String = "/graphiql",
        /** GraphiQL title */
        @DefaultValue("Simple GraphiQL Example") var title: String = "Simple GraphiQL Example"
    )
    /**
     * Configuration properties for the schema-json endpoint.
     */
    data class DgsSchemaJsonConfigurationProperties(
        /** Path to the schema-json endpoint without trailing slash. */
        @DefaultValue("/schema.json") var path: String = "/schema.json"
    )

    @PostConstruct
    fun validatePaths() {
        validatePath(this.path, "dgs.graphql.path")
        validatePath(this.graphiql.path, "dgs.graphql.graphiql.path")
        validatePath(this.schemaJson.path, "dgs.graphql.schema-json.path")
    }

    private fun validatePath(path: String, pathProperty: String) {
        if (path != "/" && (!path.startsWith("/") || path.endsWith("/"))) {
            throw IllegalArgumentException("$pathProperty must start with '/' and not end with '/' but was '$path'")
        }
    }

    companion object {
        const val DEFAULT_CONNECTION_INIT_TIMEOUT = "10s"
        val DEFAULT_CONNECTION_INIT_TIMEOUT_DURATION: Duration = Duration.ofSeconds(10)
    }
}
