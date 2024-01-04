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

package com.netflix.graphql.dgs.mvc.internal.method

import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData
import com.netflix.graphql.dgs.internal.method.ArgumentResolver
import graphql.schema.DataFetchingEnvironment
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver

/**
 * [ArgumentResolver] adapter for Spring's [HandlerMethodArgumentResolver].
 * Allows leveraging Spring MVC adapters for things such as @CookieValue annotated
 * methods.
 */
class HandlerMethodArgumentResolverAdapter(
    private val delegate: HandlerMethodArgumentResolver,
    private val webDataBinderFactory: WebDataBinderFactory? = null
) : ArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return delegate.supportsParameter(parameter)
    }

    override fun resolveArgument(parameter: MethodParameter, dfe: DataFetchingEnvironment): Any? {
        return delegate.resolveArgument(parameter, null, getRequest(dfe), webDataBinderFactory)
    }

    private fun getRequest(dfe: DataFetchingEnvironment): NativeWebRequest {
        val request = when (val requestData = DgsContext.getRequestData(dfe)) {
            is DgsWebMvcRequestData -> requestData.webRequest
            else -> throw AssertionError()
        }
        return request as NativeWebRequest
    }
}
