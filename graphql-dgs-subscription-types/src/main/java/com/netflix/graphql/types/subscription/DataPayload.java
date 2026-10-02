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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class DataPayload implements MessagePayload {
    @JsonProperty("data")
    private final Object data;

    @JsonProperty("errors")
    private final List<Object> errors;

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public DataPayload(
            @Nullable Object data,
            @Nullable List<? extends Object> errors) {
        this.data = data;
        this.errors = asList(errors);
    }

    @JsonCreator
    public DataPayload(@Nullable @JsonProperty("data") Object data) {
        this(data, List.of());
    }

    @Nullable
    public Object getData() {
        return data;
    }

    @Nullable
    public List<Object> getErrors() {
        return errors;
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
    public DataPayload copy(@Nullable Object data, @Nullable List<? extends Object> errors) {
        return new DataPayload(data, asList(errors));
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
        return other instanceof DataPayload that
                && Objects.equals(data, that.data)
                && Objects.equals(errors, that.errors);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(data);
        result = 31 * result + Objects.hashCode(errors);
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return "DataPayload(data=" + data + ", errors=" + errors + ")";
    }
}
