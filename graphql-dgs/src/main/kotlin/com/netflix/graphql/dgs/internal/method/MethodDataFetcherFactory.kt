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
import com.netflix.graphql.dgs.InputArgument
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
    private val argumentResolvers: List<ArgumentResolver>,
    private val parameterNameDiscoverer: ParameterNameDiscoverer = DefaultParameterNameDiscoverer()
) {

    private val resolvers = ArgumentResolverComposite(argumentResolvers)

    fun checkInputArgumentsAreValid(method: Method, argumentNames: Set<String>) {
        val inputArgumentResolvers = argumentResolvers.filterIsInstance<AbstractInputArgumentResolver>()
        if (inputArgumentResolvers.isEmpty()) return

        val bridgedMethod: Method = BridgeMethodResolver.findBridgedMethod(method)
        val methodParameters: List<MethodParameter> = bridgedMethod.parameters.map { parameter ->
            val methodParameter = SynthesizingMethodParameter.forParameter(parameter)
            methodParameter.initParameterNameDiscovery(parameterNameDiscoverer)
            methodParameter
        }
        val annotatedMethodParams = methodParameters.filter { it.hasParameterAnnotation(InputArgument::class.java) }
        annotatedMethodParams.forEach { m ->
            val paramName = m.parameterName ?: return@forEach
            val possibleParamNames = inputArgumentResolvers.map { it.resolveArgumentName(m) }.toSet()
            if (possibleParamNames.none { argumentNames.contains(it) }) {
                val arguments = if (argumentNames.isNotEmpty()) {
                    "Found the following argument(s) in the schema: " + argumentNames.joinToString(prefix = "[", postfix = "]")
                } else {
                    "No arguments on the field are defined in the schema."
                }
                throw DataFetcherInputArgumentSchemaMismatchException(
                    "@InputArgument is defined in ${method.declaringClass} in method `${method.name}` on parameter named `$paramName` but there is " +
                        "no matching argument in the GraphQL schema that matches the possible names: ${possibleParamNames.joinToString(prefix = "[", postfix = "]")}. " +
                        arguments
                )
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
