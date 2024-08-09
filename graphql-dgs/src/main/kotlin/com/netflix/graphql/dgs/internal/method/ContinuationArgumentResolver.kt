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

package com.netflix.graphql.dgs.internal.method

import graphql.schema.DataFetchingEnvironment
import org.springframework.core.CoroutinesUtils
import org.springframework.core.MethodParameter
import kotlin.coroutines.Continuation

/**
 * Resolves method arguments of type [Continuation], the hidden
 * final parameter for Kotlin suspend functions. This resolver returns
 * a placeholder of `null` for compatibility with helpers like
 * [CoroutinesUtils.invokeSuspendingFunction] which ignore the final
 * argument.
 */
class ContinuationArgumentResolver : ArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == Continuation::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        dfe: DataFetchingEnvironment,
    ): Any? = null
}
