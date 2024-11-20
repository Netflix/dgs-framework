/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.graphql.dgs.springgraphql.webmvc

import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.context.GraphQLContextContributor
import com.netflix.graphql.dgs.internal.DefaultDgsGraphQLContextBuilder
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData
import com.netflix.graphql.dgs.springgraphql.autoconfig.DgsSpringGraphQLConfigurationProperties
import graphql.GraphQLContext
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture

class DgsWebMvcGraphQLInterceptor(
    private val dgsDataLoaderProvider: DgsDataLoaderProvider,
    private val dgsContextBuilder: DefaultDgsGraphQLContextBuilder,
    private val dgsSpringConfigurationProperties: DgsSpringGraphQLConfigurationProperties,
    private val graphQLContextContributors: List<GraphQLContextContributor>,
) : WebGraphQlInterceptor {
    override fun intercept(
        request: WebGraphQlRequest,
        chain: WebGraphQlInterceptor.Chain,
    ): Mono<WebGraphQlResponse> {
        // We need to pass in the original server request for the dgs context
        val servletRequestAttributes =
            if (RequestContextHolder.getRequestAttributes() is ServletRequestAttributes) {
                (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes)
            } else {
                null
            }

        val dgsContext =
            if (servletRequestAttributes != null) {
                val webRequest: WebRequest = ServletWebRequest(servletRequestAttributes.request, servletRequestAttributes.response)
                dgsContextBuilder.build(DgsWebMvcRequestData(request.extensions, request.headers, webRequest))
            } else {
                dgsContextBuilder.build(DgsWebMvcRequestData(request.extensions, request.headers))
            }
        val dataLoaderRegistry = dgsDataLoaderProvider.buildRegistryWithContextSupplier {
            val graphQLContext = request.toExecutionInput().graphQLContext
            if (graphQLContextContributors.isNotEmpty()) {
                val extensions = request.extensions
                val requestData = dgsContext.requestData
                val builderForContributors = GraphQLContext.newContext()
                graphQLContextContributors.forEach { it.contribute(builderForContributors, extensions, requestData) }
                graphQLContext.putAll(builderForContributors)
            }

            graphQLContext
        }

        request.configureExecutionInput { _, builder ->
            builder
                .context(dgsContext)
                .graphQLContext(dgsContext)
                .dataLoaderRegistry(dataLoaderRegistry)
                .build()
        }




        return if (dgsSpringConfigurationProperties.webmvc.asyncdispatch.enabled) {
            chain.next(request).doFinally {
                if (dataLoaderRegistry is AutoCloseable) {
                    dataLoaderRegistry.close()
                }
            }
        } else {
            @Suppress("BlockingMethodInNonBlockingContext")
            val response = chain.next(request).block()!!
            if (dataLoaderRegistry is AutoCloseable) {
                dataLoaderRegistry.close()
            }
            return Mono.just(response)
        }
    }
}
