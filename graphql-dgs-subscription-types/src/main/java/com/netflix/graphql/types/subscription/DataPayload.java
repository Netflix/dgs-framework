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

public final class DataPayload implements MessagePayload {
    private final Object data;
    private final List<Object> errors;

    @JsonCreator
    public DataPayload(
            @JsonProperty("data") Object data,
            @JsonProperty("errors") List<Object> errors) {
        this.data = data;
        this.errors = errors == null ? List.of() : errors;
    }

    public DataPayload(Object data) {
        this(data, List.of());
    }

    public Object getData() {
        return data;
    }

    public List<Object> getErrors() {
        return errors;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof DataPayload that
                && Objects.equals(data, that.data)
                && Objects.equals(errors, that.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data, errors);
    }

    @Override
    public String toString() {
        return "DataPayload(data=" + data + ", errors=" + errors + ")";
    }
}
