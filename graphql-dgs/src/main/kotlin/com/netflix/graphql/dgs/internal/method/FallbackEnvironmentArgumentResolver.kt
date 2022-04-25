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
import org.springframework.core.MethodParameter
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.util.ClassUtils

/**
 * Resolves arguments based on the name by looking for any matching
 * arguments in the current [DataFetchingEnvironment]. Intended as
 * a fallback if no other resolvers can handle the argument.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
class FallbackEnvironmentArgumentResolver : ArgumentResolver {

    private val conversionService = DefaultConversionService()

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.parameterName != null
    }

    override fun resolveArgument(parameter: MethodParameter, dfe: DataFetchingEnvironment): Any? {
        val value = dfe.getArgument<Any?>(parameter.parameterName)
        if (ClassUtils.isAssignableValue(parameter.parameterType, value)) {
            return value
        }
        return conversionService.convert(value, parameter.parameterType)
    }
}
