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

import com.netflix.graphql.dgs.internal.DgsRequestData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.Map;
import java.util.Objects;

/**
 * Request data for reactive (WebFlux) requests.
 *
 * @see DgsRequestData
 */
public final class DgsReactiveRequestData implements DgsRequestData {
    private final Map<String, Object> extensions;
    private final HttpHeaders headers;
    private final ServerRequest serverRequest;

    /**
     * @param extensions Optional map of extensions - useful for customized GraphQL interactions between for example a gateway and dgs.
     * @param headers Http Headers
     * @param serverRequest Spring reactive {@link ServerHttpRequest}. This will only be available when deployed in a
     *                      WebFlux (non-Servlet) environment.
     */
    public DgsReactiveRequestData(
            @Nullable Map<String, ? extends Object> extensions,
            @Nullable HttpHeaders headers,
            @Nullable ServerRequest serverRequest) {
        this.extensions = castExtensions(extensions);
        this.headers = headers;
        this.serverRequest = serverRequest;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castExtensions(Map<String, ? extends Object> extensions) {
        return (Map<String, Object>) (Map<?, ?>) extensions;
    }

    public DgsReactiveRequestData(
            @Nullable Map<String, ? extends Object> extensions, @Nullable HttpHeaders headers) {
        this(extensions, headers, null);
    }

    public DgsReactiveRequestData(@Nullable Map<String, ? extends Object> extensions) {
        this(extensions, HttpHeaders.readOnlyHttpHeaders(new HttpHeaders()), null);
    }

    public DgsReactiveRequestData() {
        this(Map.of(), HttpHeaders.readOnlyHttpHeaders(new HttpHeaders()), null);
    }

    @Override
    @Nullable
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    @Override
    @Nullable
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Nullable
    public ServerRequest getServerRequest() {
        return serverRequest;
    }

    @Nullable
    public Map<String, Object> component1() {
        return extensions;
    }

    @Nullable
    public HttpHeaders component2() {
        return headers;
    }

    @Nullable
    public ServerRequest component3() {
        return serverRequest;
    }

    @NotNull
    public DgsReactiveRequestData copy(
            @Nullable Map<String, ? extends Object> extensions,
            @Nullable HttpHeaders headers,
            @Nullable ServerRequest serverRequest) {
        return new DgsReactiveRequestData(extensions, headers, serverRequest);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof DgsReactiveRequestData that
                && Objects.equals(extensions, that.extensions)
                && Objects.equals(headers, that.headers)
                && Objects.equals(serverRequest, that.serverRequest);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(extensions);
        result = 31 * result + Objects.hashCode(headers);
        result = 31 * result + Objects.hashCode(serverRequest);
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return "DgsReactiveRequestData(extensions=" + extensions + ", headers=" + headers
                + ", serverRequest=" + serverRequest + ")";
    }
}
