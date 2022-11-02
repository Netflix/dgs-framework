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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.internal.utils.TimeTracer
import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.GraphqlErrorBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

class DgsExecutionResult constructor(
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

    fun toSpringResponse(
        mapper: ObjectMapper = jacksonObjectMapper()
    ): ResponseEntity<Any> {
        val result = try {
            TimeTracer.logTime(
                { mapper.writeValueAsBytes(this.toSpecification()) },
                logger,
                "Serialized JSON result in {}ms"
            )
        } catch (ex: InvalidDefinitionException) {
            val errorMessage = "Error serializing response: ${ex.message}"
            val errorResponse = ExecutionResultImpl(GraphqlErrorBuilder.newError().message(errorMessage).build())
            logger.error(errorMessage, ex)
            mapper.writeValueAsBytes(errorResponse.toSpecification())
        }

        return ResponseEntity(
            result,
            headers,
            status
        )
    }

    // Refer to https://github.com/Netflix/dgs-framework/pull/1261 for further details.
    override fun toSpecification(): MutableMap<String, Any> {
        val spec = executionResult.toSpecification()

        if (spec["extensions"] != null && extensions.containsKey(DGS_RESPONSE_HEADERS_KEY)) {
            val extensions = spec["extensions"] as Map<*, *>

            if (extensions.size != 1) {
                spec["extensions"] = extensions.minus(DGS_RESPONSE_HEADERS_KEY)
            } else {
                spec.remove("extensions")
            }
        }

        return spec
    }

    // Refer to https://github.com/Netflix/dgs-framework/pull/1261 for further details.
    private fun addExtensionsHeaderKeyToHeader() {
        if (executionResult.extensions?.containsKey(DGS_RESPONSE_HEADERS_KEY) == true) {
            val dgsResponseHeaders = executionResult.extensions[DGS_RESPONSE_HEADERS_KEY]
            if (dgsResponseHeaders is Map<*, *> && dgsResponseHeaders.isNotEmpty()) {
                // If the HttpHeaders are empty/read-only we need to switch to a new instance that allows us
                // to store the headers that are part of the GraphQL response _extensions_.
                if (headers == HttpHeaders.EMPTY) {
                    headers = HttpHeaders()
                }

                dgsResponseHeaders.forEach {
                    if (it.key != null) {
                        headers.add(it.key.toString(), it.value?.toString())
                    }
                }
            } else {
                logger.warn(
                    "{} must be of type java.util.Map, but was {}",
                    DGS_RESPONSE_HEADERS_KEY,
                    dgsResponseHeaders?.javaClass?.name
                )
            }
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
            executionResult = checkNotNull(executionResult),
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
