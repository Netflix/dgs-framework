/*
 * Copyright 2025 Netflix, Inc.
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

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties(prefix = "dgs.graphql.spring")
data class DgsSpringGraphQLConfigurationProperties(
    val webmvc: WebMvc = WebMvc(Asyncdispatch(false)),
) {
    data class WebMvc(
        val asyncdispatch: Asyncdispatch,
    )

    data class Asyncdispatch(
        @DefaultValue("false") val enabled: Boolean,
    )
}
