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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class GraphQLError {
    private final String message;
    private final List<Object> path;
    private final List<Object> locations;
    private final GraphQLErrorExtensions extensions;
    private final String pathAsString;

    @JsonCreator
    public GraphQLError(
            @NotNull @JsonProperty("message") String message,
            @NotNull @JsonProperty("path") List<? extends Object> path,
            @NotNull @JsonProperty("locations") List<? extends Object> locations,
            @Nullable @JsonProperty("extensions") GraphQLErrorExtensions extensions) {
        this.message = message == null ? "" : message;
        this.path = path == null ? List.of() : asList(path);
        this.locations = locations == null ? List.of() : asList(locations);
        this.extensions = extensions;
        this.pathAsString = this.path.stream().map(String::valueOf).collect(Collectors.joining("."));
    }

    public GraphQLError(@NotNull String message, @Nullable GraphQLErrorExtensions extensions) {
        this(message, List.of(), List.of(), extensions);
    }

    public GraphQLError(@NotNull String message) {
        this(message, List.of(), List.of(), null);
    }

    @NotNull
    public String getMessage() {
        return message;
    }

    @NotNull
    public List<Object> getPath() {
        return path;
    }

    @NotNull
    public List<Object> getLocations() {
        return locations;
    }

    @Nullable
    public GraphQLErrorExtensions getExtensions() {
        return extensions;
    }

    @NotNull
    public String getPathAsString() {
        return pathAsString;
    }

    @NotNull
    public String component1() {
        return message;
    }

    @NotNull
    public List<Object> component2() {
        return path;
    }

    @NotNull
    public List<Object> component3() {
        return locations;
    }

    @Nullable
    public GraphQLErrorExtensions component4() {
        return extensions;
    }

    @NotNull
    public GraphQLError copy(
            @NotNull String message,
            @NotNull List<? extends Object> path,
            @NotNull List<? extends Object> locations,
            @Nullable GraphQLErrorExtensions extensions) {
        return new GraphQLError(
                Objects.requireNonNull(message, "message"),
                asList(Objects.requireNonNull(path, "path")),
                asList(Objects.requireNonNull(locations, "locations")),
                extensions);
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
        return other instanceof GraphQLError that
                && Objects.equals(message, that.message)
                && Objects.equals(path, that.path)
                && Objects.equals(locations, that.locations)
                && Objects.equals(extensions, that.extensions);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(message);
        result = 31 * result + Objects.hashCode(path);
        result = 31 * result + Objects.hashCode(locations);
        result = 31 * result + Objects.hashCode(extensions);
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return "GraphQLError(message=" + message + ", path=" + path + ", locations=" + locations
                + ", extensions=" + extensions + ")";
    }
}
