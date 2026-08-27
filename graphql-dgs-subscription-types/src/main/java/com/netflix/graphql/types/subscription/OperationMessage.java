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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

public final class OperationMessage {
    private final String type;

    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION, defaultImpl = EmptyPayload.class)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = EmptyPayload.class),
            @JsonSubTypes.Type(value = DataPayload.class),
            @JsonSubTypes.Type(value = QueryPayload.class)
    })
    private final Object payload;

    private final String id;

    @JsonCreator
    public OperationMessage(
            @JsonProperty(value = "type", required = true) String type,
            @JsonProperty("payload") Object payload,
            @JsonProperty(value = "id", required = false) String id) {
        this.type = type;
        this.payload = payload;
        this.id = id == null ? "" : id;
    }

    public OperationMessage(String type, Object payload) {
        this(type, payload, "");
    }

    public OperationMessage(String type) {
        this(type, null, "");
    }

    public String getType() {
        return type;
    }

    public Object getPayload() {
        return payload;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof OperationMessage that
                && Objects.equals(type, that.type)
                && Objects.equals(payload, that.payload)
                && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, payload, id);
    }

    @Override
    public String toString() {
        return "OperationMessage(type=" + type + ", payload=" + payload + ", id=" + id + ")";
    }
}
