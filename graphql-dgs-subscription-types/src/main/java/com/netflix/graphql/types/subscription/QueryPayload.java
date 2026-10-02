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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class QueryPayload implements MessagePayload {
    @JsonProperty("variables")
    private final Map<String, Object> variables;

    @JsonProperty("extensions")
    private final Map<String, Object> extensions;

    @JsonProperty("operationName")
    private final String operationName;

    @JsonProperty(value = "query", required = true)
    @JsonSetter(nulls = Nulls.FAIL)
    private final String query;

    @JsonProperty("key")
    @JsonSetter(nulls = Nulls.FAIL)
    private final String key;

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public QueryPayload(
            @Nullable Map<String, ? extends Object> variables,
            @Nullable Map<String, ? extends Object> extensions,
            @Nullable String operationName,
            @NotNull @Language("graphql") String query,
            @NotNull String key) {
        this.variables = asMap(variables);
        this.extensions = asMap(extensions);
        this.operationName = operationName;
        this.query = Objects.requireNonNull(query, "query");
        this.key = Objects.requireNonNull(key, "key");
    }

    public QueryPayload(
            @Nullable Map<String, ? extends Object> variables,
            @Nullable Map<String, ? extends Object> extensions,
            @Nullable String operationName,
            @NotNull @Language("graphql") String query) {
        this(variables, extensions, operationName, query, "");
    }

    @JsonCreator
    public QueryPayload(
            @NotNull @JsonProperty(value = "query", required = true) @Language("graphql") String query) {
        this(Map.of(), Map.of(), null, query, "");
    }

    @Nullable
    public Map<String, Object> getVariables() {
        return variables;
    }

    @Nullable
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    @Nullable
    public String getOperationName() {
        return operationName;
    }

    @NotNull
    public String getQuery() {
        return query;
    }

    @NotNull
    public String getKey() {
        return key;
    }

    @Nullable
    public Map<String, Object> component1() {
        return variables;
    }

    @Nullable
    public Map<String, Object> component2() {
        return extensions;
    }

    @Nullable
    public String component3() {
        return operationName;
    }

    @NotNull
    public String component4() {
        return query;
    }

    @NotNull
    public String component5() {
        return key;
    }

    @NotNull
    public QueryPayload copy(
            @Nullable Map<String, ? extends Object> variables,
            @Nullable Map<String, ? extends Object> extensions,
            @Nullable String operationName,
            @NotNull String query,
            @NotNull String key) {
        return new QueryPayload(
                asMap(variables),
                asMap(extensions),
                operationName,
                Objects.requireNonNull(query, "query"),
                Objects.requireNonNull(key, "key"));
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
        return other instanceof QueryPayload that
                && Objects.equals(variables, that.variables)
                && Objects.equals(extensions, that.extensions)
                && Objects.equals(operationName, that.operationName)
                && Objects.equals(query, that.query)
                && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(variables);
        result = 31 * result + Objects.hashCode(extensions);
        result = 31 * result + Objects.hashCode(operationName);
        result = 31 * result + Objects.hashCode(query);
        result = 31 * result + Objects.hashCode(key);
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return "QueryPayload(variables=" + variables + ", extensions=" + extensions
                + ", operationName=" + operationName + ", query=" + query + ", key=" + key + ")";
    }
}
