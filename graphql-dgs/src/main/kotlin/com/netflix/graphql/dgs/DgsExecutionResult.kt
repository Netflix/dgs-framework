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

package com.netflix.graphql.dgs

import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import java.util.function.Consumer

class DgsExecutionResult(
    private val executionResult: ExecutionResult,
    val headers: HttpHeaders,
    val status: HttpStatus = HttpStatus.OK,
) : ExecutionResult by executionResult {
    /**
     * Facilitate the construction of a [DgsExecutionResult] instance.
     */
    class Builder {
        var executionResult: ExecutionResult = DEFAULT_EXECUTION_RESULT
            private set

        fun executionResult(executionResult: ExecutionResult) = apply { this.executionResult = executionResult }

        fun executionResult(executionResultBuilder: ExecutionResultImpl.Builder<*>) =
            apply { this.executionResult = executionResultBuilder.build() }

        var headers: HttpHeaders = HttpHeaders.EMPTY
            private set

        fun headers(headers: HttpHeaders) = apply { this.headers = headers }

        var status: HttpStatus = HttpStatus.OK
            private set

        fun status(status: HttpStatus) = apply { this.status = status }

        fun build() =
            DgsExecutionResult(
                executionResult = executionResult,
                headers = headers,
                status = status,
            )

        companion object {
            private val DEFAULT_EXECUTION_RESULT = ExecutionResultImpl.newExecutionResult().build()
        }
    }

    override fun transform(builderConsumer: Consumer<ExecutionResult.Builder<*>>): ExecutionResult =
        executionResult.transform(builderConsumer)

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
