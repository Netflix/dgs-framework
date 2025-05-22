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

package com.netflix.graphql.dgs.springgraphql.webflux

import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveGraphQLContextBuilder
import com.netflix.graphql.dgs.reactive.internal.DgsReactiveRequestData
import org.dataloader.DataLoaderRegistry
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.core.publisher.Mono

class DgsWebFluxGraphQLInterceptor(
    private val dgsDataLoaderProvider: DgsDataLoaderProvider,
    private val dgsReactiveGraphQLContextBuilder: DefaultDgsReactiveGraphQLContextBuilder,
) : WebGraphQlInterceptor {
    override fun intercept(
        request: WebGraphQlRequest,
        chain: WebGraphQlInterceptor.Chain,
    ): Mono<WebGraphQlResponse> =
        Mono
            .deferContextual { ctx ->
                val webExchange = ServerWebExchangeContextFilter.getExchange(ctx).get()
                val serverRequest = ServerRequest.create(webExchange, emptyList())
                dgsReactiveGraphQLContextBuilder.build(
                    DgsReactiveRequestData(
                        request.extensions,
                        request.headers,
                        serverRequest,
                    ),
                )
            }.flatMap { dgsContext ->
                var dataLoaderRegistry: DataLoaderRegistry? = null
                request.configureExecutionInput { e, builder ->
                    dataLoaderRegistry = dgsDataLoaderProvider.buildRegistryWithContextSupplier { e.graphQLContext }
                    builder
                        .context(dgsContext)
                        .graphQLContext(dgsContext)
                        .dataLoaderRegistry(dataLoaderRegistry)
                        .build()
                }

                chain.next(request).doFinally {
                    if (dataLoaderRegistry is AutoCloseable) {
                        (dataLoaderRegistry as AutoCloseable).close()
                    }
                }
            }
}
