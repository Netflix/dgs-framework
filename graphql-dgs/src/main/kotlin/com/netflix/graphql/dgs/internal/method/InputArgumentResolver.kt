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

import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.internal.InputObjectMapper
import org.springframework.core.MethodParameter
import org.springframework.core.annotation.MergedAnnotation

/**
 * Resolves method arguments annotated with [InputArgument].
 *
 * Argument conversion responsibilities are handled by the supplied [InputObjectMapper].
 */
class InputArgumentResolver(
    inputObjectMapper: InputObjectMapper,
) : AbstractInputArgumentResolver(inputObjectMapper) {
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.hasParameterAnnotation(InputArgument::class.java)

    override fun resolveArgumentName(parameter: MethodParameter): String {
        val annotation =
            parameter.getParameterAnnotation(InputArgument::class.java)
                ?: throw IllegalArgumentException(
                    "Unsupported parameter type [${parameter.parameterType.name}]. supportsParameter should be called first.",
                )

        val mergedAnnotation = MergedAnnotation.from(annotation).synthesize()

        return mergedAnnotation.name.ifBlank { parameter.parameterName }
            ?: throw IllegalArgumentException(
                "Name for argument of type [${parameter.nestedParameterType.name}}" +
                    " not specified, and parameter name information not found in class file either.",
            )
    }
}
