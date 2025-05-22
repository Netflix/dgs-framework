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

package com.netflix.graphql.dgs.reactive.internal.method

import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.internal.method.ArgumentResolver
import com.netflix.graphql.dgs.reactive.internal.DgsReactiveRequestData
import graphql.schema.DataFetchingEnvironment
import org.springframework.core.MethodParameter
import org.springframework.web.reactive.BindingContext
import org.springframework.web.reactive.result.method.SyncHandlerMethodArgumentResolver

/**
 * [ArgumentResolver] adapter for [SyncHandlerMethodArgumentResolver],
 * which allows leveraging existing Spring WebFlux argument resolvers.
 */
class SyncHandlerMethodArgumentResolverAdapter(
    private val delegate: SyncHandlerMethodArgumentResolver,
    private val bindingContext: BindingContext,
) : ArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean = delegate.supportsParameter(parameter)

    override fun resolveArgument(
        parameter: MethodParameter,
        dfe: DataFetchingEnvironment,
    ): Any? {
        val requestData =
            DgsContext.getRequestData(dfe) as? DgsReactiveRequestData
                ?: throw IllegalStateException("DgsReactiveRequestData not found")
        val request =
            requestData.serverRequest
                ?: throw IllegalStateException("serverRequest is not set")
        val exchange = request.exchange()

        return delegate.resolveArgument(parameter, bindingContext, exchange).share().block()
    }
}
