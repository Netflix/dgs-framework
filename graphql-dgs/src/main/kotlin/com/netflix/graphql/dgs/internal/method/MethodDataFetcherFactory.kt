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
import graphql.schema.DataFetcher
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.ParameterNameDiscoverer
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

    fun createDataFetcher(bean: Any, method: Method): DataFetcher<Any?> {
        return DataFetcherInvoker(
            dgsComponent = bean,
            method = method,
            resolvers = resolvers,
            parameterNameDiscoverer = parameterNameDiscoverer
        )
    }
}
