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

import java.util.List;
import java.util.Objects;

public final class SSEDataPayload implements MessagePayload {
    private final Object data;
    private final List<Object> errors;
    private final String subId;
    private final String type;

    @JsonCreator
    public SSEDataPayload(
            @JsonProperty("data") Object data,
            @JsonProperty("errors") List<Object> errors,
            @JsonProperty("subId") String subId,
            @JsonProperty("type") String type) {
        this.data = data;
        this.errors = errors == null ? List.of() : errors;
        this.subId = subId;
        this.type = type;
    }

    public SSEDataPayload(Object data, List<Object> errors, String subId) {
        this(data, errors, subId, OperationMessageType.SSE_GQL_SUBSCRIPTION_DATA);
    }

    public SSEDataPayload(Object data, String subId) {
        this(data, List.of(), subId, OperationMessageType.SSE_GQL_SUBSCRIPTION_DATA);
    }

    public Object getData() {
        return data;
    }

    public List<Object> getErrors() {
        return errors;
    }

    public String getSubId() {
        return subId;
    }

    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object other) {
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
        return Objects.hash(data, errors, subId, type);
    }

    @Override
    public String toString() {
        return "SSEDataPayload(data=" + data + ", errors=" + errors + ", subId=" + subId + ", type=" + type + ")";
    }
}
