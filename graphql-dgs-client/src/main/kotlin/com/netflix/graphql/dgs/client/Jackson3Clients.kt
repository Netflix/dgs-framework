/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.graphql.dgs.client

import org.springframework.http.MediaType

/**
 * Shared utilities for Jackson 3 client classes.
 * Separate from [GraphQLClients] to avoid loading Jackson 2 classes.
 */
internal object Jackson3Clients {
    internal val defaultHeaders: Map<String, List<String>> =
        mapOf(
            "Accept" to listOf(MediaType.APPLICATION_JSON.toString()),
            "Content-Type" to listOf(MediaType.APPLICATION_JSON.toString()),
        )

    internal fun toRequestMap(
        query: String,
        operationName: String?,
        variables: Map<String, Any?>,
    ): Map<String, Any?> =
        mapOf(
            "query" to query,
            "operationName" to operationName,
            "variables" to variables,
        )
}
