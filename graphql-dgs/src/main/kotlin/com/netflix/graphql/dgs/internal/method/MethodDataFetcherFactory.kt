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
import com.netflix.graphql.dgs.internal.DataFetcherInvoker
import graphql.TrivialDataFetcher
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.FieldCoordinates
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.MethodParameter
import org.springframework.core.ParameterNameDiscoverer
import org.springframework.core.annotation.MergedAnnotations
import org.springframework.core.task.AsyncTaskExecutor
import java.lang.reflect.Method
import kotlin.jvm.optionals.getOrNull

/**
 * Factory for constructing a [DataFetcher] given a [DgsData] annotated method.
 *
 * Resolving of method arguments is handled by the supplied [argument resolvers][ArgumentResolver].
 */
class MethodDataFetcherFactory(
    argumentResolvers: List<ArgumentResolver>,
    internal val parameterNameDiscoverer: ParameterNameDiscoverer = DefaultParameterNameDiscoverer(),
    private val asyncTaskExecutor: AsyncTaskExecutor? = null
) {

    private val resolvers = ArgumentResolverComposite(argumentResolvers)

    fun createDataFetcher(bean: Any, method: Method, fieldCoordinates: FieldCoordinates): DataFetcher<Any?> {
        if (isTrivial(method, fieldCoordinates)) {
            val methodDataFetcher = DataFetcherInvoker(
                dgsComponent = bean,
                method = method,
                resolvers = resolvers,
                parameterNameDiscoverer = parameterNameDiscoverer,
                taskExecutor = null
            )
            return object : TrivialDataFetcher<Any?> {
                override fun get(environment: DataFetchingEnvironment): Any? {
                    return methodDataFetcher.get(environment)
                }

                override fun toString(): String {
                    return "TrivialMethodDataFetcher{field=$fieldCoordinates}"
                }
            }
        }

        return DataFetcherInvoker(
            dgsComponent = bean,
            method = method,
            resolvers = resolvers,
            parameterNameDiscoverer = parameterNameDiscoverer,
            taskExecutor = asyncTaskExecutor
        )
    }

    internal fun getSelectedArgumentResolver(methodParameter: MethodParameter): ArgumentResolver? {
        return resolvers.getArgumentResolver(methodParameter)
    }

    private fun isTrivial(method: Method, coordinates: FieldCoordinates): Boolean {
        val annotation = MergedAnnotations.from(method).stream(DgsData::class.java).filter { annotation ->
            annotation.getString("parentType") == coordinates.typeName &&
                annotation.getString("field") == coordinates.fieldName
        }.findFirst().getOrNull()
        return annotation?.getBoolean("trivial") ?: false
    }
}
