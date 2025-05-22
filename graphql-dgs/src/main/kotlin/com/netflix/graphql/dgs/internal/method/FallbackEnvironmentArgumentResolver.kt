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

import com.netflix.graphql.dgs.internal.InputObjectMapper
import graphql.schema.DataFetchingEnvironment
import org.springframework.core.MethodParameter
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

/**
 * Resolves arguments based on the name by looking for any matching
 * arguments in the current [DataFetchingEnvironment]. Intended as
 * a fallback if no other resolvers can handle the argument.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
class FallbackEnvironmentArgumentResolver(
    inputObjectMapper: InputObjectMapper,
) : AbstractInputArgumentResolver(inputObjectMapper) {
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterName != null

    override fun resolveArgumentName(parameter: MethodParameter): String? = parameter.parameterName
}
