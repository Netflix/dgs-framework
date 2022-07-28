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

package com.netflix.graphql.dgs.webflux.handlers

import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerRequest

object GraphQLMediaTypes {
    private val GRAPHQL_MEDIA_TYPE = MediaType("application", "graphql")
    val ACCEPTABLE_MEDIA_TYPES = listOf(GRAPHQL_MEDIA_TYPE, MediaType.APPLICATION_JSON)

    fun isApplicationGraphQL(request: ServerRequest): Boolean {
        return request.headers().contentType().map { GRAPHQL_MEDIA_TYPE.includes(it) }.orElse(false)
    }
}
