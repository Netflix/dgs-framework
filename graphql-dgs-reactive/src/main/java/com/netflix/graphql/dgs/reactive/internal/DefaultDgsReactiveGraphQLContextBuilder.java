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

package com.netflix.graphql.dgs.reactive.internal;

import com.netflix.graphql.dgs.context.DgsContext;
import com.netflix.graphql.dgs.context.ReactiveDgsContext;
import com.netflix.graphql.dgs.reactive.DgsReactiveCustomContextBuilderWithRequest;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

public class DefaultDgsReactiveGraphQLContextBuilder {
    private final Optional<DgsReactiveCustomContextBuilderWithRequest<?>> dgsReactiveCustomContextBuilderWithRequest;

    public DefaultDgsReactiveGraphQLContextBuilder(
            Optional<DgsReactiveCustomContextBuilderWithRequest<?>> dgsReactiveCustomContextBuilderWithRequest) {
        this.dgsReactiveCustomContextBuilderWithRequest = dgsReactiveCustomContextBuilderWithRequest;
    }

    public DefaultDgsReactiveGraphQLContextBuilder() {
        this(Optional.empty());
    }

    public Mono<DgsContext> build(DgsReactiveRequestData dgsRequestData) {
        Mono<?> customContext;
        if (dgsReactiveCustomContextBuilderWithRequest.isPresent()) {
            Map<String, Object> extensions =
                    dgsRequestData != null && dgsRequestData.getExtensions() != null
                            ? dgsRequestData.getExtensions()
                            : Map.of();
            HttpHeaders headers =
                    dgsRequestData != null && dgsRequestData.getHeaders() != null
                            ? dgsRequestData.getHeaders()
                            : new HttpHeaders();
            customContext =
                    dgsReactiveCustomContextBuilderWithRequest
                            .get()
                            .build(
                                    extensions,
                                    HttpHeaders.readOnlyHttpHeaders(headers),
                                    dgsRequestData != null ? dgsRequestData.getServerRequest() : null);
        } else {
            customContext = Mono.empty();
        }

        Mono<?> resolvedCustomContext = customContext;
        return Mono.deferContextual(context ->
                resolvedCustomContext
                        .<DgsContext>map(custom -> new ReactiveDgsContext(custom, dgsRequestData, context))
                        .defaultIfEmpty(new ReactiveDgsContext(null, dgsRequestData, context)));
    }
}
