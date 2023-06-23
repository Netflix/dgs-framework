/*
 * Copyright 2023 Netflix, Inc.
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

package com.netflix.graphql.dgs.autoconfig

import com.netflix.graphql.dgs.internal.DefaultDgsGraphQLContextBuilder
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData
import graphql.GraphQLContext
import jakarta.servlet.http.HttpServletRequest
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import reactor.core.publisher.Mono
import java.util.*
import java.util.concurrent.CompletableFuture

class DgsGraphQLInterceptor(
    private val dgsDataLoaderProvider: DgsDataLoaderProvider,
    private val dgsContextBuilder: DefaultDgsGraphQLContextBuilder
) : WebGraphQlInterceptor {
    override fun intercept(request: WebGraphQlRequest, chain: WebGraphQlInterceptor.Chain): Mono<WebGraphQlResponse> {
        // We need to pass in the original server request for the dgs context
        val servletRequest: HttpServletRequest? = if (RequestContextHolder.getRequestAttributes() is ServletRequestAttributes) {
            (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request
        } else null

        val webRequest: WebRequest = ServletWebRequest(servletRequest)
        val dgsContext = dgsContextBuilder.build(DgsWebMvcRequestData(request.extensions, request.headers, webRequest))
        val graphQLContextFuture = CompletableFuture<GraphQLContext>()
        val dataLoaderRegistry = dgsDataLoaderProvider.buildRegistryWithContextSupplier { graphQLContextFuture.get() }

        request.configureExecutionInput { _, builder ->
            builder
                .context(dgsContext)
                .graphQLContext(dgsContext)
                .dataLoaderRegistry(dataLoaderRegistry).build()
        }
        graphQLContextFuture.complete(request.toExecutionInput().graphQLContext)

        return chain.next(request)
    }
}
