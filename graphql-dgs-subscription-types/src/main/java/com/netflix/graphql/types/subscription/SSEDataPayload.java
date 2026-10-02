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

package com.netflix.graphql.types.subscription;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class SSEDataPayload implements MessagePayload {
    @JsonProperty("data")
    private final Object data;

    @JsonProperty("errors")
    private final List<Object> errors;

    @JsonProperty(value = "subId", required = true)
    @JsonSetter(nulls = Nulls.FAIL)
    private final String subId;

    @JsonProperty("type")
    @JsonSetter(nulls = Nulls.FAIL)
    private final String type;

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public SSEDataPayload(
            @Nullable Object data,
            @Nullable List<? extends Object> errors,
            @NotNull String subId,
            @NotNull String type) {
        this.data = data;
        this.errors = asList(errors);
        this.subId = Objects.requireNonNull(subId, "subId");
        this.type = Objects.requireNonNull(type, "type");
    }

    public SSEDataPayload(@Nullable Object data, @Nullable List<Object> errors, @NotNull String subId) {
        this(data, errors, subId, OperationMessageType.SSE_GQL_SUBSCRIPTION_DATA);
    }

    @JsonCreator
    public SSEDataPayload(
            @Nullable @JsonProperty("data") Object data,
            @NotNull @JsonProperty(value = "subId", required = true) String subId) {
        this(data, List.of(), subId, OperationMessageType.SSE_GQL_SUBSCRIPTION_DATA);
    }

    @Nullable
    public Object getData() {
        return data;
    }

    @Nullable
    public List<Object> getErrors() {
        return errors;
    }

    @NotNull
    public String getSubId() {
        return subId;
    }

    @NotNull
    public String getType() {
        return type;
    }

    @Nullable
    public Object component1() {
        return data;
    }

    @Nullable
    public List<Object> component2() {
        return errors;
    }

    @NotNull
    public String component3() {
        return subId;
    }

    @NotNull
    public String component4() {
        return type;
    }

    @NotNull
    public SSEDataPayload copy(@Nullable Object data, @Nullable List<? extends Object> errors, @NotNull String subId, @NotNull String type) {
        return new SSEDataPayload(
                data,
                asList(errors),
                Objects.requireNonNull(subId, "subId"),
                Objects.requireNonNull(type, "type"));
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> asList(List<? extends T> list) {
        return (List<T>) list;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof SSEDataPayload that
                && Objects.equals(data, that.data)
                && Objects.equals(errors, that.errors)
                && Objects.equals(subId, that.subId)
                && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(data);
        result = 31 * result + Objects.hashCode(errors);
        result = 31 * result + Objects.hashCode(subId);
        result = 31 * result + Objects.hashCode(type);
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return "SSEDataPayload(data=" + data + ", errors=" + errors + ", subId=" + subId + ", type=" + type + ")";
    }
}
