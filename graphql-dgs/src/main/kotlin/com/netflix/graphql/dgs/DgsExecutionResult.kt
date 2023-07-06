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

package com.netflix.graphql.dgs

import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

class DgsExecutionResult(
    private val executionResult: ExecutionResult,
    private var headers: HttpHeaders,
    val status: HttpStatus
) : ExecutionResult by executionResult {

    init {
        addExtensionsHeaderKeyToHeader()
    }

    /** Read-Only reference to the HTTP Headers. */
    fun headers(): HttpHeaders {
        return HttpHeaders.readOnlyHttpHeaders(headers)
    }

    fun toSpringResponse(): ResponseEntity<Any> {
        return ResponseEntity(
            toSpecification(),
            headers,
            status
        )
    }

    // Refer to https://github.com/Netflix/dgs-framework/pull/1261 for further details.
    override fun toSpecification(): MutableMap<String, Any> {
        val spec = executionResult.toSpecification()

        val extensions = spec["extensions"] as Map<*, *>?
            ?: return spec

        if (DGS_RESPONSE_HEADERS_KEY in extensions) {
            if (extensions.size == 1) {
                spec -= "extensions"
            } else {
                spec["extensions"] = extensions - DGS_RESPONSE_HEADERS_KEY
            }
        }

        return spec
    }

    // Refer to https://github.com/Netflix/dgs-framework/pull/1261 for further details.
    private fun addExtensionsHeaderKeyToHeader() {
        val extensions = executionResult.extensions
            ?: return

        val dgsResponseHeaders = extensions[DGS_RESPONSE_HEADERS_KEY]
            ?: return

        if (dgsResponseHeaders is Map<*, *> && dgsResponseHeaders.isNotEmpty()) {
            // If the HttpHeaders are empty/read-only we need to switch to a new instance that allows us
            // to store the headers that are part of the GraphQL response _extensions_.

            val updatedHeaders = HttpHeaders.writableHttpHeaders(headers)

            dgsResponseHeaders.forEach { (key, value) ->
                if (key != null) {
                    updatedHeaders.add(key.toString(), value?.toString())
                }
            }
            headers = HttpHeaders.readOnlyHttpHeaders(updatedHeaders)
        } else {
            logger.warn(
                "{} must be of type java.util.Map, but was {}",
                DGS_RESPONSE_HEADERS_KEY,
                dgsResponseHeaders.javaClass.name
            )
        }
    }

    /**
     * Facilitate the construction of a [DgsExecutionResult] instance.
     */
    class Builder {
        var executionResult: ExecutionResult = DEFAULT_EXECUTION_RESULT
            private set

        fun executionResult(executionResult: ExecutionResult) =
            apply { this.executionResult = executionResult }

        fun executionResult(executionResultBuilder: ExecutionResultImpl.Builder) =
            apply { this.executionResult = executionResultBuilder.build() }

        var headers: HttpHeaders = HttpHeaders.EMPTY
            private set

        fun headers(headers: HttpHeaders) = apply { this.headers = headers }

        var status: HttpStatus = HttpStatus.OK
            private set

        fun status(status: HttpStatus) = apply { this.status = status }

        fun build() = DgsExecutionResult(
            executionResult = executionResult,
            headers = headers,
            status = status
        )

        companion object {
            private val DEFAULT_EXECUTION_RESULT = ExecutionResultImpl.newExecutionResult().build()
        }
    }

    companion object {
        // defined in here and DgsRestController, for backwards compatibility. Keep these two variables synced.
        const val DGS_RESPONSE_HEADERS_KEY = "dgs-response-headers"
        private val logger: Logger = LoggerFactory.getLogger(DgsExecutionResult::class.java)

        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
