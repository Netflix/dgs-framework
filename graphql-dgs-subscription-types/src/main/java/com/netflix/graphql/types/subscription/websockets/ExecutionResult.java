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

package com.netflix.graphql.types.subscription.websockets;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import graphql.GraphQLError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class ExecutionResult {
    private final Object data;
    private final List<GraphQLError> errors;

    @JsonCreator
    public ExecutionResult(
            @Nullable @JsonProperty("data") Object data,
            @NotNull @JsonProperty("errors") List<? extends GraphQLError> errors) {
        this.data = data;
        this.errors = asList(errors);
    }

    @Nullable
    public Object getData() {
        return data;
    }

    @NotNull
    public List<GraphQLError> getErrors() {
        return errors;
    }

    @Nullable
    public Object component1() {
        return data;
    }

    @NotNull
    public List<GraphQLError> component2() {
        return errors;
    }

    @NotNull
    public ExecutionResult copy(@Nullable Object data, @NotNull List<? extends GraphQLError> errors) {
        return new ExecutionResult(data, asList(Objects.requireNonNull(errors, "errors")));
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
        return other instanceof ExecutionResult that
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
        return "ExecutionResult(data=" + data + ", errors=" + errors + ")";
    }
}
