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

package com.netflix.graphql.dgs.internal;

import com.netflix.graphql.dgs.context.DgsContext;
import com.netflix.graphql.dgs.context.DgsCustomContextBuilder;
import com.netflix.graphql.dgs.context.DgsCustomContextBuilderWithRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import java.util.Map;
import java.util.Optional;

public class DefaultDgsGraphQLContextBuilder {
    private static final Logger logger = LoggerFactory.getLogger(DefaultDgsGraphQLContextBuilder.class);

    private final Optional<DgsCustomContextBuilder<?>> dgsCustomContextBuilder;
    private final Optional<DgsCustomContextBuilderWithRequest<?>> dgsCustomContextBuilderWithRequest;

    public DefaultDgsGraphQLContextBuilder(
            Optional<DgsCustomContextBuilder<?>> dgsCustomContextBuilder,
            Optional<DgsCustomContextBuilderWithRequest<?>> dgsCustomContextBuilderWithRequest) {
        this.dgsCustomContextBuilder = dgsCustomContextBuilder;
        this.dgsCustomContextBuilderWithRequest = dgsCustomContextBuilderWithRequest;
    }

    public DefaultDgsGraphQLContextBuilder(Optional<DgsCustomContextBuilder<?>> dgsCustomContextBuilder) {
        this(dgsCustomContextBuilder, Optional.empty());
    }

    public DgsContext build(DgsWebMvcRequestData dgsRequestData) {
        long start = System.currentTimeMillis();
        DgsContext context = buildDgsContext(dgsRequestData);
        logger.debug("Created DGS context in {}ms", System.currentTimeMillis() - start);
        return context;
    }

    private DgsContext buildDgsContext(DgsWebMvcRequestData dgsRequestData) {
        Object customContext;
        if (dgsCustomContextBuilderWithRequest.isPresent()) {
            Map<String, Object> extensions =
                    dgsRequestData != null && dgsRequestData.getExtensions() != null
                            ? dgsRequestData.getExtensions()
                            : Map.of();
            HttpHeaders headers = dgsRequestData != null && dgsRequestData.getHeaders() != null
                    ? dgsRequestData.getHeaders()
                    : new HttpHeaders();
            customContext = dgsCustomContextBuilderWithRequest
                    .get()
                    .build(
                            extensions,
                            HttpHeaders.readOnlyHttpHeaders(headers),
                            dgsRequestData != null ? dgsRequestData.getWebRequest() : null);
        } else if (dgsCustomContextBuilder.isPresent()) {
            customContext = dgsCustomContextBuilder.get().build();
        } else {
            // This is for backwards compatibility - we previously made DefaultRequestData the custom context if no
            // custom context was provided.
            customContext = dgsRequestData;
        }

        return new DgsContext(customContext, dgsRequestData);
    }
}
