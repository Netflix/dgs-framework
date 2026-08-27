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

import java.util.Map;
import java.util.Objects;

/**
 * @deprecated Use {@link com.netflix.graphql.dgs.context.DgsContext#getRequestData} instead.
 */
@Deprecated
public final class DefaultRequestData {
    private final Map<String, Object> extensions;
    private final HttpHeaders headers;

    public DefaultRequestData(Map<String, Object> extensions, HttpHeaders headers) {
        this.extensions = extensions;
        this.headers = headers;
    }

    /**
     * @deprecated Use {@link com.netflix.graphql.dgs.context.DgsContext#getRequestData} instead.
     */
    @Deprecated
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    /**
     * @deprecated Use {@link com.netflix.graphql.dgs.context.DgsContext#getRequestData} instead.
     */
    @Deprecated
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof DefaultRequestData that
                && Objects.equals(extensions, that.extensions)
                && Objects.equals(headers, that.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(extensions, headers);
    }

    @Override
    public String toString() {
        return "DefaultRequestData(extensions=" + extensions + ", headers=" + headers + ")";
    }
}
