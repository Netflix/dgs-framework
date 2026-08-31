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
    public DgsWebMvcRequestData(Map<String, Object> extensions, HttpHeaders headers, WebRequest webRequest) {
        this.extensions = extensions;
        this.headers = headers;
        this.webRequest = webRequest;
    }

    public DgsWebMvcRequestData(Map<String, Object> extensions, HttpHeaders headers) {
        this(extensions, headers, null);
    }

    public DgsWebMvcRequestData(Map<String, Object> extensions) {
        this(extensions, null, null);
    }

    public DgsWebMvcRequestData() {
        this(null, null, null);
    }

    @Override
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    public WebRequest getWebRequest() {
        return webRequest;
    }

    @Override
    public boolean equals(Object other) {
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
        return Objects.hash(extensions, headers, webRequest);
    }

    @Override
    public String toString() {
        return "DgsWebMvcRequestData(extensions=" + extensions + ", headers=" + headers
                + ", webRequest=" + webRequest + ")";
    }
}
