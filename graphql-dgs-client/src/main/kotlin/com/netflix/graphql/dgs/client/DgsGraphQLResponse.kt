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
import org.slf4j.LoggerFactory

/**
 * Canonical response contract for the DGS GraphQL client. Jackson-version-agnostic —
 * program against this type and your code survives Jackson upgrades.
 *
 * Concrete implementations:
 *  - [DefaultDgsGraphQLResponse] — default impl backed by any [com.netflix.graphql.dgs.json.DgsJsonMapper]. Construct directly when you need a response instance (e.g. in tests).
 *  - [GraphQLResponse] (deprecated, Jackson 2) — kept for back-compat during the transition; will be removed in a future release
 */
interface DgsGraphQLResponse {
    val json: String

    val headers: Map<String, List<String>>

    val parsed: DocumentContext

    val data: Map<String, Any>

    val errors: List<GraphQLError>

    fun <T> dataAsObject(clazz: Class<T>): T

    /**
     * Extract a value at [path]. Returns whatever type the caller binds to — for JSON objects
     * this is a Map. Use [extractValueAsObject] to deserialize into a specific class instead.
     */
    fun <T> extractValue(path: String): T {
        val dataPath = getDataPath(path)
        try {
            return parsed.read(dataPath)
        } catch (ex: Exception) {
            logger.warn("Error extracting path '$path' from data: '$data'")
            throw ex
        }
    }

    fun <T> extractValueAsObject(
        path: String,
        clazz: Class<T>,
    ): T {
        val dataPath = getDataPath(path)
        try {
            return parsed.read(dataPath, clazz)
        } catch (ex: Exception) {
            logger.warn("Error extracting path '$path' from data: '$data'")
            throw ex
        }
    }

    /** Use this overload for generic types like `List<Foo>`. */
    fun <T> extractValueAsObject(
        path: String,
        typeRef: TypeRef<T>,
    ): T {
        val dataPath = getDataPath(path)
        try {
            return parsed.read(dataPath, typeRef)
        } catch (ex: Exception) {
            logger.warn("Error extracting path '$path' from data: '$data'")
            throw ex
        }
    }

    fun getRequestDetails(): RequestDetails? = extractValueAsObject("gatewayRequestDetails", RequestDetails::class.java)

    fun hasErrors(): Boolean = errors.isNotEmpty()

    companion object {
        private val logger = LoggerFactory.getLogger(DgsGraphQLResponse::class.java)

        fun getDataPath(path: String): String =
            if (path == "data" || path.startsWith("data.")) {
                path
            } else {
                "data.$path"
            }
    }
}
