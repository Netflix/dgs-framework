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
public final class GraphQLErrorExtensions {
    private final ErrorType errorType;
    private final String errorDetail;
    private final String origin;
    private final GraphQLErrorDebugInfo debugInfo;
    private final Object classification;

    @JsonCreator
    public GraphQLErrorExtensions(
            @JsonProperty("errorType") ErrorType errorType,
            @JsonProperty("errorDetail") String errorDetail,
            @JsonProperty("origin") String origin,
            @JsonProperty("debugInfo") GraphQLErrorDebugInfo debugInfo,
            @JsonProperty("classification") Object classification) {
        this.errorType = errorType;
        this.errorDetail = errorDetail;
        this.origin = origin == null ? "" : origin;
        this.debugInfo = debugInfo == null ? new GraphQLErrorDebugInfo() : debugInfo;
        this.classification = classification == null ? "" : classification;
    }

    public GraphQLErrorExtensions(ErrorType errorType) {
        this(errorType, null, "", new GraphQLErrorDebugInfo(), "");
    }

    public GraphQLErrorExtensions() {
        this(null, null, "", new GraphQLErrorDebugInfo(), "");
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public String getOrigin() {
        return origin;
    }

    public GraphQLErrorDebugInfo getDebugInfo() {
        return debugInfo;
    }

    public Object getClassification() {
        return classification;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof GraphQLErrorExtensions that
                && errorType == that.errorType
                && Objects.equals(errorDetail, that.errorDetail)
                && Objects.equals(origin, that.origin)
                && Objects.equals(debugInfo, that.debugInfo)
                && Objects.equals(classification, that.classification);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorType, errorDetail, origin, debugInfo, classification);
    }

    @Override
    public String toString() {
        return "GraphQLErrorExtensions(errorType=" + errorType + ", errorDetail=" + errorDetail
                + ", origin=" + origin + ", debugInfo=" + debugInfo + ", classification=" + classification + ")";
    }
}
