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

package com.netflix.graphql.dgs.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class RequestDetails {
    private final String requestId;
    private final String edgarLink;

    @JsonCreator
    public RequestDetails(
            @JsonProperty("requestId") String requestId,
            @JsonProperty("edgarLink") String edgarLink) {
        this.requestId = requestId;
        this.edgarLink = edgarLink;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getEdgarLink() {
        return edgarLink;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof RequestDetails that
                && Objects.equals(requestId, that.requestId)
                && Objects.equals(edgarLink, that.edgarLink);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, edgarLink);
    }

    @Override
    public String toString() {
        return "RequestDetails(requestId=" + requestId + ", edgarLink=" + edgarLink + ")";
    }
}
