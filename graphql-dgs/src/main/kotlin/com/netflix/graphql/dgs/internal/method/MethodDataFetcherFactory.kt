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

import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.exceptions.DataFetcherInputArgumentSchemaMismatchException
import com.netflix.graphql.dgs.internal.DataFetcherInvoker
import graphql.schema.DataFetcher
import org.springframework.core.BridgeMethodResolver
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.MethodParameter
import org.springframework.core.ParameterNameDiscoverer
import org.springframework.core.annotation.SynthesizingMethodParameter
import java.lang.reflect.Method

/**
 * Factory for constructing a [DataFetcher] given a [DgsData] annotated method.
 *
 * Resolving of method arguments is handled by the supplied [argument resolvers][ArgumentResolver].
 */
class MethodDataFetcherFactory(
    argumentResolvers: List<ArgumentResolver>,
    private val parameterNameDiscoverer: ParameterNameDiscoverer = DefaultParameterNameDiscoverer()
) {

    private val resolvers = ArgumentResolverComposite(argumentResolvers)

    fun checkInputArgumentsAreValid(method: Method, argumentNames: Set<String>) {
        val bridgedMethod: Method = BridgeMethodResolver.findBridgedMethod(method)
        val methodParameters: List<MethodParameter> = bridgedMethod.parameters.map { parameter ->
            val methodParameter = SynthesizingMethodParameter.forParameter(parameter)
            methodParameter.initParameterNameDiscovery(parameterNameDiscoverer)
            methodParameter
        }

        methodParameters.forEach { m ->
            val selectedArgResolver = resolvers.getArgumentResolver(m) ?: return@forEach
            if (selectedArgResolver is AbstractInputArgumentResolver) {
                val argName = selectedArgResolver.resolveArgumentName(m) ?: return@forEach
                if (!argumentNames.contains(argName)) {
                    val paramName = m.parameterName ?: return@forEach
                    val arguments = if (argumentNames.isNotEmpty()) {
                        "Found the following argument(s) in the schema: " + argumentNames.joinToString(prefix = "[", postfix = "]")
                    } else {
                        "No arguments on the field are defined in the schema."
                    }

                    when (selectedArgResolver) {
                        is InputArgumentResolver -> throw DataFetcherInputArgumentSchemaMismatchException(
                            "@InputArgument(name = \"$argName\") defined in ${method.declaringClass} in method `${method.name}` " +
                                "on parameter named `$paramName` has no matching argument with name `$argName` in the GraphQL schema. " +
                                arguments
                        )
                        is FallbackEnvironmentArgumentResolver -> throw DataFetcherInputArgumentSchemaMismatchException(
                            "Parameter named `$paramName` in ${method.declaringClass} in method `${method.name}` " +
                                "has no matching argument with name `$argName` in the GraphQL schema. " +
                                "Consider annotating the method parameter with @InputArgument(name = ) or renaming the " +
                                "method parameter name to match the corresponding field argument on the GraphQL schema. " +
                                arguments
                        )
                    }
                }
            }
        }
    }

    fun createDataFetcher(bean: Any, method: Method): DataFetcher<Any?> {
        return DataFetcherInvoker(
            dgsComponent = bean,
            method = method,
            resolvers = resolvers,
            parameterNameDiscoverer = parameterNameDiscoverer
        )
    }
}
