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

package com.netflix.graphql.dgs.springgraphql.webflux;

import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider;
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveGraphQLContextBuilder;
import com.netflix.graphql.dgs.reactive.internal.DgsReactiveRequestData;
import org.dataloader.DataLoaderRegistry;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class DgsWebFluxGraphQLInterceptor implements WebGraphQlInterceptor {
    private final DgsDataLoaderProvider dgsDataLoaderProvider;
    private final DefaultDgsReactiveGraphQLContextBuilder dgsReactiveGraphQLContextBuilder;

    public DgsWebFluxGraphQLInterceptor(
            DgsDataLoaderProvider dgsDataLoaderProvider,
            DefaultDgsReactiveGraphQLContextBuilder dgsReactiveGraphQLContextBuilder) {
        this.dgsDataLoaderProvider = dgsDataLoaderProvider;
        this.dgsReactiveGraphQLContextBuilder = dgsReactiveGraphQLContextBuilder;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        return Mono.deferContextual(ctx -> {
                    ServerWebExchange webExchange =
                            ServerWebExchangeContextFilter.getExchange(ctx).get();
                    ServerRequest serverRequest = ServerRequest.create(webExchange, List.of());
                    return dgsReactiveGraphQLContextBuilder.build(new DgsReactiveRequestData(
                            request.getExtensions(), request.getHeaders(), serverRequest));
                })
                .flatMap(dgsContext -> {
                    AtomicReference<DataLoaderRegistry> dataLoaderRegistry = new AtomicReference<>();
                    request.configureExecutionInput((executionInput, builder) -> {
                        dataLoaderRegistry.set(dgsDataLoaderProvider.buildRegistryWithContextSupplier(
                                executionInput::getGraphQLContext));
                        return builder.graphQLContext(dgsContext)
                                .dataLoaderRegistry(dataLoaderRegistry.get())
                                .build();
                    });

                    return chain.next(request).doFinally(signalType -> {
                        if (dataLoaderRegistry.get() instanceof AutoCloseable closeable) {
                            try {
                                closeable.close();
                            } catch (Exception ex) {
                                throw new IllegalStateException(ex);
                            }
                        }
                    });
                });
    }
}
