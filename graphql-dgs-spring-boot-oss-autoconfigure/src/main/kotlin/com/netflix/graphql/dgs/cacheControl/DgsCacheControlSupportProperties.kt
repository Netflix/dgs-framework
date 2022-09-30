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

package com.netflix.graphql.dgs.cacheControl

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties(prefix = DgsCacheControlSupportProperties.PREFIX)
@ConstructorBinding
data class DgsCacheControlSupportProperties(
    /** Enables/Disables support for [Apollo Server-side caching using @cacheControl](https://www.apollographql.com/docs/apollo-server/performance/caching). */
    @DefaultValue("$DEFAULT_ENABLED")
    var enabled: Boolean = DEFAULT_ENABLED,
    @DefaultValue("-1")
    var defaultMaxAge: Int? = null
) {

    companion object {
        const val DEFAULT_ENABLED = false
        const val PREFIX: String = "dgs.graphql.cache-control"
    }
}
