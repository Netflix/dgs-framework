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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class GraphQLErrorDebugInfo {
    @JsonProperty("subquery")
    @JsonSetter(nulls = Nulls.FAIL)
    private final String subquery;

    @JsonProperty("variables")
    @JsonSetter(nulls = Nulls.FAIL)
    private final Map<String, Object> variables;

    @JsonAnySetter
    private final Map<String, Object> additionalInformation;

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public GraphQLErrorDebugInfo(
            @NotNull String subquery,
            @NotNull Map<String, ? extends Object> variables,
            @NotNull Map<String, ? extends Object> additionalInformation) {
        this.subquery = Objects.requireNonNull(subquery, "subquery");
        this.variables = asMap(Objects.requireNonNull(variables, "variables"));
        this.additionalInformation = asMap(Objects.requireNonNull(additionalInformation, "additionalInformation"));
    }

    public GraphQLErrorDebugInfo(@NotNull String subquery, @NotNull Map<String, ? extends Object> variables) {
        this(subquery, variables, new HashMap<>());
    }

    @JsonCreator
    public GraphQLErrorDebugInfo() {
        this("", Map.of(), new HashMap<>());
    }

    @NotNull
    public String getSubquery() {
        return subquery;
    }

    @NotNull
    public Map<String, Object> getVariables() {
        return variables;
    }

    @NotNull
    @JsonAnyGetter
    public Map<String, Object> getAdditionalInformation() {
        return additionalInformation;
    }

    @NotNull
    public String component1() {
        return subquery;
    }

    @NotNull
    public Map<String, Object> component2() {
        return variables;
    }

    @NotNull
    public Map<String, Object> component3() {
        return additionalInformation;
    }

    @NotNull
    public GraphQLErrorDebugInfo copy(
            @NotNull String subquery,
            @NotNull Map<String, ? extends Object> variables,
            @NotNull Map<String, ? extends Object> additionalInformation) {
        return new GraphQLErrorDebugInfo(
                Objects.requireNonNull(subquery, "subquery"),
                asMap(Objects.requireNonNull(variables, "variables")),
                asMap(Objects.requireNonNull(additionalInformation, "additionalInformation")));
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<String, T> asMap(Map<String, ? extends T> map) {
        return (Map<String, T>) map;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof GraphQLErrorDebugInfo that
                && Objects.equals(subquery, that.subquery)
                && Objects.equals(variables, that.variables)
                && Objects.equals(additionalInformation, that.additionalInformation);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(subquery);
        result = 31 * result + Objects.hashCode(variables);
        result = 31 * result + Objects.hashCode(additionalInformation);
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return "GraphQLErrorDebugInfo(subquery=" + subquery + ", variables=" + variables
                + ", additionalInformation=" + additionalInformation + ")";
    }
}
