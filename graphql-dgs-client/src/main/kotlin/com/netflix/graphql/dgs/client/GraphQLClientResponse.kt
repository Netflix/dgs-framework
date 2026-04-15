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

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.TypeRef

/**
 * Common interface for GraphQL response types, providing Jackson-version-agnostic access
 * to response data. Both [GraphQLResponse] (Jackson 2) and [Jackson3GraphQLResponse] (Jackson 3)
 * implement this interface.
 *
 * Users who want to write code that works with either Jackson version should program against
 * this interface rather than the concrete response classes.
 */
interface GraphQLClientResponse {
    /** The raw JSON response string. */
    val json: String

    /** HTTP response headers. */
    val headers: Map<String, List<String>>

    /** A JsonPath DocumentContext for the parsed response. */
    val parsed: DocumentContext

    /** Map representation of the response data. */
    val data: Map<String, Any>

    /** List of GraphQL errors in the response. */
    val errors: List<GraphQLError>

    /** Deserialize the response data into the given class. */
    fun <T> dataAsObject(clazz: Class<T>): T

    /**
     * Extract values given a JsonPath. The return type will be whatever type you expect.
     * For JSON objects, a Map is returned. If you want to deserialize to a class, use [extractValueAsObject] instead.
     */
    fun <T> extractValue(path: String): T

    /** Extract values given a JsonPath and deserialize into the given class. */
    fun <T> extractValueAsObject(
        path: String,
        clazz: Class<T>,
    ): T

    /** Extract values given a JsonPath and deserialize into the given TypeRef. Use this for Lists of a specific type. */
    fun <T> extractValueAsObject(
        path: String,
        typeRef: TypeRef<T>,
    ): T

    /** Extracts RequestDetails from the response if available. Returns null otherwise. */
    fun getRequestDetails(): RequestDetails?

    /** Returns true if the response contains errors. */
    fun hasErrors(): Boolean

    companion object {
        fun getDataPath(path: String): String =
            if (path == "data" || path.startsWith("data.")) {
                path
            } else {
                "data.$path"
            }
    }
}
