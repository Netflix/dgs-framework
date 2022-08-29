/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.graphql.dgs.subscriptions.websockets

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration
import javax.annotation.PostConstruct

@ConstructorBinding
@ConfigurationProperties(prefix = "dgs.graphql.websocket")
@Suppress("ConfigurationProperties")
data class DgsWebSocketConfigurationProperties(
    @DefaultValue("/subscriptions") var path: String = "/subscriptions",
    /** Connection Initialization timeout. */
    @DefaultValue(CONNECTION_INIT_TIMEOUT) var connectionInitTimeout: Duration
) {

    @PostConstruct
    fun validatePaths() {
        validatePath(this.path, "dgs.graphql.websocket.path")
    }

    private fun validatePath(path: String, pathProperty: String) {
        if (path != "/" && (!path.startsWith("/") || path.endsWith("/"))) {
            throw IllegalArgumentException("$pathProperty must start with '/' and not end with '/' but was '$path'")
        }
    }

    companion object {
        const val CONNECTION_INIT_TIMEOUT = "10s"
    }
}
