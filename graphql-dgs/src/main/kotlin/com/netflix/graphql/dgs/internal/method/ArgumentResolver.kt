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

package com.netflix.graphql.dgs.internal.method

import graphql.schema.DataFetchingEnvironment
import org.springframework.core.MethodParameter
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

/**
 * Resolves method parameters into argument values for @DgsData annotated methods.
 *
 * See [org.springframework.web.method.support.HandlerMethodArgumentResolver]
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
interface ArgumentResolver {
    /**
     * Determine whether the given [MethodParameter] is supported by this resolver.
     *
     * @param parameter the method parameter to check
     * @return Boolean indicating if this resolver supports the supplied parameter
     */
    fun supportsParameter(parameter: MethodParameter): Boolean

    /**
     * Resolves a method parameter into an argument value for a @DgsData annotated method.
     *
     * @param parameter the method parameter to resolve. This parameter must
     * have previously been passed to [supportsParameter] which must
     * have returned `true`.
     * @param dfe the associated [DataFetchingEnvironment] for the current request
     * @return the resolved argument value
     */
    fun resolveArgument(
        parameter: MethodParameter,
        dfe: DataFetchingEnvironment,
    ): Any?
}
