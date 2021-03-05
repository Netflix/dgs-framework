/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.graphql.dgs.metrics.micrometer

import graphql.ExecutionResult
import graphql.GraphQLError
import graphql.InvalidSyntaxError
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.validation.ValidationError

internal object DgsGraphQLMetricsInstrumentationUtils {
    private val instrumentationIgnores = setOf("__typename", "__Schema", "__Type")
    private const val TAG_VALUE_UNKNOWN = "unknown"
    private const val TAG_VALUE_NONE = "none"

    fun resolveDataFetcherTagValue(parameters: InstrumentationFieldFetchParameters): String {
        val type = parameters.executionStepInfo.parent.type
        val parentType = if (type is GraphQLNonNull) {
            type.wrappedType as GraphQLObjectType
        } else {
            type as GraphQLObjectType
        }

        return "${parentType.name}.${parameters.executionStepInfo.path.segmentName}"
    }

    fun shouldIgnoreTag(tag: String): Boolean {
        return instrumentationIgnores.find { tag.contains(it) } != null
    }

    fun sanitizeErrorPaths(executionResult: ExecutionResult): Collection<ErrorTagValues> {
        var dedupeErrorPaths: Map<String, ErrorTagValues> = emptyMap()
        executionResult.errors.forEach { error ->
            val errorPath: List<Any>
            val errorType: String
            val errorDetail = errorDetailExtension(error)
            when (error) {
                is ValidationError -> {
                    errorPath = error.queryPath ?: emptyList()
                    errorType = errorType(error)
                }
                is InvalidSyntaxError -> {
                    errorPath = emptyList()
                    errorType = errorType(error)
                }
                else -> {
                    errorPath = error.path ?: emptyList()
                    errorType = errorTypeExtension(error)
                }
            }
            val sanitizedPath = errorPath.map { iter ->
                if (iter.toString().toIntOrNull() != null) "number"
                else iter.toString()
            }.toString()
            // in case of batch loaders, eliminate duplicate instances of the same error at different indices
            if (!dedupeErrorPaths.contains(sanitizedPath)) {
                dedupeErrorPaths = dedupeErrorPaths
                    .plus(Pair(sanitizedPath, ErrorTagValues(sanitizedPath, errorType, errorDetail)))
            }
        }
        return dedupeErrorPaths.values
    }

    private fun <T : GraphQLError> errorType(error: T): String {
        return error.errorType?.toString() ?: TAG_VALUE_UNKNOWN
    }

    private fun <T : GraphQLError> errorTypeExtension(error: T): String {
        return extension(error, "errorType", TAG_VALUE_UNKNOWN)
    }

    private fun <T : GraphQLError> errorDetailExtension(error: T): String {
        return extension(error, "errorDetail", TAG_VALUE_NONE)
    }

    private fun <T : GraphQLError> extension(error: T, key: String, default: String): String {
        return error.extensions?.get(key)?.toString() ?: default
    }

    internal data class ErrorTagValues(val path: String, val type: String, val detail: String)
}
