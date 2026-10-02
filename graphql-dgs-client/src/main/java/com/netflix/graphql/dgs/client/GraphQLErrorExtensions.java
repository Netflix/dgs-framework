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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
            @Nullable @JsonProperty("errorType") ErrorType errorType,
            @Nullable @JsonProperty("errorDetail") String errorDetail,
            @NotNull @JsonProperty("origin") String origin,
            @NotNull @JsonProperty("debugInfo") GraphQLErrorDebugInfo debugInfo,
            @NotNull @JsonProperty("classification") Object classification) {
        this.errorType = errorType;
        this.errorDetail = errorDetail;
        this.origin = origin == null ? "" : origin;
        this.debugInfo = debugInfo == null ? new GraphQLErrorDebugInfo() : debugInfo;
        this.classification = classification == null ? "" : classification;
    }

    public GraphQLErrorExtensions(@Nullable ErrorType errorType) {
        this(errorType, null, "", new GraphQLErrorDebugInfo(), "");
    }

    public GraphQLErrorExtensions() {
        this(null, null, "", new GraphQLErrorDebugInfo(), "");
    }

    @Nullable
    public ErrorType getErrorType() {
        return errorType;
    }

    @Nullable
    public String getErrorDetail() {
        return errorDetail;
    }

    @NotNull
    public String getOrigin() {
        return origin;
    }

    @NotNull
    public GraphQLErrorDebugInfo getDebugInfo() {
        return debugInfo;
    }

    @NotNull
    public Object getClassification() {
        return classification;
    }

    @Nullable
    public ErrorType component1() {
        return errorType;
    }

    @Nullable
    public String component2() {
        return errorDetail;
    }

    @NotNull
    public String component3() {
        return origin;
    }

    @NotNull
    public GraphQLErrorDebugInfo component4() {
        return debugInfo;
    }

    @NotNull
    public Object component5() {
        return classification;
    }

    @NotNull
    public GraphQLErrorExtensions copy(
            @Nullable ErrorType errorType,
            @Nullable String errorDetail,
            @NotNull String origin,
            @NotNull GraphQLErrorDebugInfo debugInfo,
            @NotNull Object classification) {
        return new GraphQLErrorExtensions(
                errorType,
                errorDetail,
                Objects.requireNonNull(origin, "origin"),
                Objects.requireNonNull(debugInfo, "debugInfo"),
                Objects.requireNonNull(classification, "classification"));
    }

    @Override
    public boolean equals(@Nullable Object other) {
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
        int result = Objects.hashCode(errorType);
        result = 31 * result + Objects.hashCode(errorDetail);
        result = 31 * result + Objects.hashCode(origin);
        result = 31 * result + Objects.hashCode(debugInfo);
        result = 31 * result + Objects.hashCode(classification);
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return "GraphQLErrorExtensions(errorType=" + errorType + ", errorDetail=" + errorDetail
                + ", origin=" + origin + ", debugInfo=" + debugInfo + ", classification=" + classification + ")";
    }
}
