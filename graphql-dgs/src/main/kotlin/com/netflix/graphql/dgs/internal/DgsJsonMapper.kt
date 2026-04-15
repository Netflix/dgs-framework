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

package com.netflix.graphql.dgs.internal

import com.jayway.jsonpath.Configuration

/**
 * Abstraction over Jackson ObjectMapper/JsonMapper to allow DGS to work with
 * either Jackson 3 (default) or Jackson 2 (opt-in via graphql-dgs-jackson2 module).
 */
interface DgsJsonMapper {
    fun writeValueAsString(value: Any): String

    fun <T> readValue(
        content: String,
        clazz: Class<T>,
    ): T

    fun <T> convertValue(
        fromValue: Any,
        toClass: Class<T>,
    ): T

    fun jsonPathConfiguration(): Configuration
}
