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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Resolves method parameters by delegating to the supplied list of
 * [argument resolvers][ArgumentResolver].
 * Previously resolved method parameters are cached.
 */
class ArgumentResolverComposite(
    private val argumentResolvers: List<ArgumentResolver>,
) : ArgumentResolver {
    private val argumentResolverCache: ConcurrentMap<MethodParameter, ArgumentResolver> = ConcurrentHashMap()

    override fun supportsParameter(parameter: MethodParameter): Boolean = getArgumentResolver(parameter) != null

    override fun resolveArgument(
        parameter: MethodParameter,
        dfe: DataFetchingEnvironment,
    ): Any? {
        val resolver =
            getArgumentResolver(parameter)
                ?: throw IllegalArgumentException(
                    "Unsupported parameter type [${parameter.parameterType.name}]. supportsParameter should be called first.",
                )
        return resolver.resolveArgument(parameter, dfe)
    }

    internal fun getArgumentResolver(parameter: MethodParameter): ArgumentResolver? {
        val cachedResolver = this.argumentResolverCache[parameter]
        if (cachedResolver != null) {
            return cachedResolver
        }
        for (resolver in argumentResolvers) {
            if (resolver.supportsParameter(parameter)) {
                argumentResolverCache[parameter] = resolver
                return resolver
            }
        }
        return null
    }
}
