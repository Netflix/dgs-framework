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

package com.netflix.graphql.dgs.exceptions

import com.netflix.graphql.types.errors.ErrorType
import com.netflix.graphql.types.errors.TypedGraphQLError
import graphql.execution.ResultPath

abstract class DgsException(
    override val message: String,
    override val cause: Exception? = null,
    val errorType: ErrorType = ErrorType.UNKNOWN
) : RuntimeException(message, cause) {
    fun toGraphQlError(path: ResultPath = ResultPath.rootPath()): TypedGraphQLError {
        return TypedGraphQLError
            .newBuilder()
            .errorType(errorType)
            .path(path)
            .message(message)
            .extensions(mapOf("class" to this::class.java.name))
            .build()
    }
}
