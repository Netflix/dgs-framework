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

import java.util.Objects;

public final class Error {
    @JsonProperty("message")
    @JsonSetter(nulls = Nulls.FAIL)
    private final String message;

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public Error(@NotNull String message) {
        this.message = Objects.requireNonNull(message, "message");
    }

    @JsonCreator
    public Error() {
        this("");
    }

    @NotNull
    public String getMessage() {
        return message;
    }

    @NotNull
    public String component1() {
        return message;
    }

    @NotNull
    public Error copy(@NotNull String message) {
        return new Error(Objects.requireNonNull(message, "message"));
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Error that && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(message);
    }

    @NotNull
    @Override
    public String toString() {
        return "Error(message=" + message + ")";
    }
}
