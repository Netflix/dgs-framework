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

package com.netflix.graphql.dgs.exceptions

import com.netflix.graphql.types.errors.ErrorType
import com.netflix.graphql.types.errors.TypedGraphQLError
import graphql.execution.ResultPath
import org.slf4j.event.Level

abstract class DgsException(
    override val message: String,
    override val cause: Exception? = null,
    val errorType: ErrorType = ErrorType.UNKNOWN,
    val logLevel: Level = Level.ERROR,
) : RuntimeException(message, cause) {
    // Explicit constructor without logLevel for Java backward compatibility
    constructor(
        message: String,
        cause: Exception? = null,
        errorType: ErrorType = ErrorType.UNKNOWN,
    ) : this(message, cause, errorType, Level.ERROR)

    companion object {
        const val EXTENSION_CLASS_KEY = "class"
    }

    fun toGraphQlError(path: ResultPath? = null): TypedGraphQLError =
        TypedGraphQLError
            .newBuilder()
            .apply {
                if (path != null) {
                    path(path)
                }
            }.errorType(errorType)
            .message(message)
            .extensions(mapOf(EXTENSION_CLASS_KEY to this::class.java.name))
            .build()
}
