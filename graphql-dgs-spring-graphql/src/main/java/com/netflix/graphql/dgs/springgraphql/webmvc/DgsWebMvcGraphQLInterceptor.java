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

package com.netflix.graphql.dgs.springgraphql.webmvc;

import com.netflix.graphql.dgs.context.DgsContext;
import com.netflix.graphql.dgs.context.GraphQLContextContributor;
import com.netflix.graphql.dgs.internal.DefaultDgsGraphQLContextBuilder;
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider;
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData;
import com.netflix.graphql.dgs.springgraphql.autoconfig.DgsSpringGraphQLConfigurationProperties;
import org.dataloader.DataLoaderRegistry;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class DgsWebMvcGraphQLInterceptor implements WebGraphQlInterceptor {
    private final DgsDataLoaderProvider dgsDataLoaderProvider;
    private final DefaultDgsGraphQLContextBuilder dgsContextBuilder;
    private final DgsSpringGraphQLConfigurationProperties dgsSpringConfigurationProperties;
    private final List<GraphQLContextContributor> graphQLContextContributors;

    public DgsWebMvcGraphQLInterceptor(
            DgsDataLoaderProvider dgsDataLoaderProvider,
            DefaultDgsGraphQLContextBuilder dgsContextBuilder,
            DgsSpringGraphQLConfigurationProperties dgsSpringConfigurationProperties,
            List<GraphQLContextContributor> graphQLContextContributors) {
        this.dgsDataLoaderProvider = dgsDataLoaderProvider;
        this.dgsContextBuilder = dgsContextBuilder;
        this.dgsSpringConfigurationProperties = dgsSpringConfigurationProperties;
        this.graphQLContextContributors = graphQLContextContributors;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        // We need to pass in the original server request for the dgs context
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes servletRequestAttributes =
                requestAttributes instanceof ServletRequestAttributes attributes ? attributes : null;

        DgsContext dgsContext;
        if (servletRequestAttributes != null) {
            WebRequest webRequest =
                    new ServletWebRequest(servletRequestAttributes.getRequest(), servletRequestAttributes.getResponse());
            dgsContext = dgsContextBuilder.build(
                    new DgsWebMvcRequestData(request.getExtensions(), request.getHeaders(), webRequest));
        } else {
            dgsContext =
                    dgsContextBuilder.build(new DgsWebMvcRequestData(request.getExtensions(), request.getHeaders()));
        }

        AtomicReference<DataLoaderRegistry> dataLoaderRegistry = new AtomicReference<>();
        request.configureExecutionInput((executionInput, builder) -> {
            dataLoaderRegistry.set(
                    dgsDataLoaderProvider.buildRegistryWithContextSupplier(executionInput::getGraphQLContext));

            return builder.graphQLContext(dgsContext)
                    .dataLoaderRegistry(dataLoaderRegistry.get())
                    .build();
        });

        if (dgsSpringConfigurationProperties.getWebmvc().getAsyncdispatch().isEnabled()) {
            return chain.next(request).doFinally(signalType -> closeRegistry(dataLoaderRegistry.get()));
        }
        WebGraphQlResponse response = chain.next(request).block();
        closeRegistry(dataLoaderRegistry.get());
        return Mono.just(response);
    }

    private static void closeRegistry(DataLoaderRegistry dataLoaderRegistry) {
        if (dataLoaderRegistry instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
