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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class GraphQLErrorDebugInfo {
    private final String subquery;
    private final Map<String, Object> variables;

    @JsonAnySetter
    private final Map<String, Object> additionalInformation;

    @JsonCreator
    public GraphQLErrorDebugInfo(
            @JsonProperty("subquery") String subquery,
            @JsonProperty("variables") Map<String, Object> variables,
            Map<String, Object> additionalInformation) {
        this.subquery = subquery == null ? "" : subquery;
        this.variables = variables == null ? Map.of() : variables;
        this.additionalInformation = additionalInformation == null ? new HashMap<>() : additionalInformation;
    }

    public GraphQLErrorDebugInfo(String subquery, Map<String, Object> variables) {
        this(subquery, variables, new HashMap<>());
    }

    public GraphQLErrorDebugInfo() {
        this("", Map.of(), new HashMap<>());
    }

    public String getSubquery() {
        return subquery;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalInformation() {
        return additionalInformation;
    }

    @Override
    public boolean equals(Object other) {
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
        return Objects.hash(subquery, variables, additionalInformation);
    }

    @Override
    public String toString() {
        return "GraphQLErrorDebugInfo(subquery=" + subquery + ", variables=" + variables
                + ", additionalInformation=" + additionalInformation + ")";
    }
}
