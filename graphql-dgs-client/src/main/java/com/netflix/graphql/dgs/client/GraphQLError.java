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
            @JsonProperty("message") String message,
            @JsonProperty("path") List<Object> path,
            @JsonProperty("locations") List<Object> locations,
            @JsonProperty("extensions") GraphQLErrorExtensions extensions) {
        this.message = message == null ? "" : message;
        this.path = path == null ? List.of() : path;
        this.locations = locations == null ? List.of() : locations;
        this.extensions = extensions;
        this.pathAsString = this.path.stream().map(String::valueOf).collect(Collectors.joining("."));
    }

    public GraphQLError(String message, GraphQLErrorExtensions extensions) {
        this(message, List.of(), List.of(), extensions);
    }

    public GraphQLError(String message) {
        this(message, List.of(), List.of(), null);
    }

    public String getMessage() {
        return message;
    }

    public List<Object> getPath() {
        return path;
    }

    public List<Object> getLocations() {
        return locations;
    }

    public GraphQLErrorExtensions getExtensions() {
        return extensions;
    }

    public String getPathAsString() {
        return pathAsString;
    }

    @Override
    public boolean equals(Object other) {
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
        return Objects.hash(message, path, locations, extensions);
    }

    @Override
    public String toString() {
        return "GraphQLError(message=" + message + ", path=" + path + ", locations=" + locations
                + ", extensions=" + extensions + ")";
    }
}
