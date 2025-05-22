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

import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import graphql.schema.DataFetchingEnvironment
import org.springframework.context.ApplicationContext
import org.springframework.core.MethodParameter

/**
 * Resolves method arguments for parameters of type [DataFetchingEnvironment]
 * or [DgsDataFetchingEnvironment].
 */
class DataFetchingEnvironmentArgumentResolver(
    private val ctx: ApplicationContext,
) : ArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == DgsDataFetchingEnvironment::class.java ||
            parameter.parameterType == DataFetchingEnvironment::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        dfe: DataFetchingEnvironment,
    ): Any {
        if (parameter.parameterType == DgsDataFetchingEnvironment::class.java && dfe !is DgsDataFetchingEnvironment) {
            return DgsDataFetchingEnvironment(dfe, ctx)
        }
        return dfe
    }
}
