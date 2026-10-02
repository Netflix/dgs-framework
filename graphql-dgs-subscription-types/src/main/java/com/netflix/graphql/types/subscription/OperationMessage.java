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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class OperationMessage {
    @JsonProperty(value = "type", required = true)
    @JsonSetter(nulls = Nulls.FAIL)
    private final String type;

    @JsonProperty("payload")
    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION, defaultImpl = EmptyPayload.class)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = EmptyPayload.class),
            @JsonSubTypes.Type(value = DataPayload.class),
            @JsonSubTypes.Type(value = QueryPayload.class)
    })
    private final Object payload;

    @JsonProperty("id")
    private final String id;

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public OperationMessage(
            @NotNull String type,
            @Nullable Object payload,
            @Nullable String id) {
        this.type = Objects.requireNonNull(type, "type");
        this.payload = payload;
        this.id = id;
    }

    @JsonCreator
    public OperationMessage(@NotNull @JsonProperty(value = "type", required = true) String type) {
        this(type, null, "");
    }

    public OperationMessage(@NotNull String type, @Nullable Object payload) {
        this(type, payload, "");
    }

    @NotNull
    public String getType() {
        return type;
    }

    @Nullable
    public Object getPayload() {
        return payload;
    }

    @Nullable
    public String getId() {
        return id;
    }

    @NotNull
    public String component1() {
        return type;
    }

    @Nullable
    public Object component2() {
        return payload;
    }

    @Nullable
    public String component3() {
        return id;
    }

    @NotNull
    public OperationMessage copy(@NotNull String type, @Nullable Object payload, @Nullable String id) {
        return new OperationMessage(Objects.requireNonNull(type, "type"), payload, id);
    }

    @Override
    public boolean equals(@Nullable Object other) {
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
        int result = Objects.hashCode(type);
        result = 31 * result + Objects.hashCode(payload);
        result = 31 * result + Objects.hashCode(id);
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return "OperationMessage(type=" + type + ", payload=" + payload + ", id=" + id + ")";
    }
}
