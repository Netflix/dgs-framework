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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;
import java.util.Objects;

/** Request data for servlet (WebMVC) based requests. */
public final class DgsWebMvcRequestData implements DgsRequestData {
    private final Map<String, Object> extensions;
    private final HttpHeaders headers;
    private final WebRequest webRequest;

    /**
     * @param extensions Optional map of extensions - useful for customized GraphQL interactions between for example
     *                   a gateway and dgs.
     * @param headers Http Headers
     * @param webRequest Spring {@link WebRequest}. This will only be available when deployed in a WebMVC
     *                   (Servlet based) environment.
     */
    public DgsWebMvcRequestData(
            @Nullable Map<String, ? extends Object> extensions,
            @Nullable HttpHeaders headers,
            @Nullable WebRequest webRequest) {
        this.extensions = castExtensions(extensions);
        this.headers = headers;
        this.webRequest = webRequest;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castExtensions(Map<String, ? extends Object> extensions) {
        return (Map<String, Object>) (Map<?, ?>) extensions;
    }

    public DgsWebMvcRequestData(
            @Nullable Map<String, ? extends Object> extensions, @Nullable HttpHeaders headers) {
        this(extensions, headers, null);
    }

    public DgsWebMvcRequestData(@Nullable Map<String, ? extends Object> extensions) {
        this(extensions, null, null);
    }

    public DgsWebMvcRequestData() {
        this(null, null, null);
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
    public WebRequest getWebRequest() {
        return webRequest;
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
    public WebRequest component3() {
        return webRequest;
    }

    @NotNull
    public DgsWebMvcRequestData copy(
            @Nullable Map<String, ? extends Object> extensions,
            @Nullable HttpHeaders headers,
            @Nullable WebRequest webRequest) {
        return new DgsWebMvcRequestData(extensions, headers, webRequest);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof DgsWebMvcRequestData that
                && Objects.equals(extensions, that.extensions)
                && Objects.equals(headers, that.headers)
                && Objects.equals(webRequest, that.webRequest);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(extensions);
        result = 31 * result + Objects.hashCode(headers);
        result = 31 * result + Objects.hashCode(webRequest);
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return "DgsWebMvcRequestData(extensions=" + extensions + ", headers=" + headers
                + ", webRequest=" + webRequest + ")";
    }
}
