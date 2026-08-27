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
import org.intellij.lang.annotations.Language;

import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class QueryPayload implements MessagePayload {
    private final Map<String, Object> variables;
    private final Map<String, Object> extensions;
    private final String operationName;
    private final String query;
    private final String key;

    @JsonCreator
    public QueryPayload(
            @JsonProperty("variables") Map<String, Object> variables,
            @JsonProperty("extensions") Map<String, Object> extensions,
            @JsonProperty("operationName") String operationName,
            @JsonProperty(value = "query", required = true) @Language("graphql") String query,
            @JsonProperty("key") String key) {
        this.variables = variables == null ? Map.of() : variables;
        this.extensions = extensions == null ? Map.of() : extensions;
        this.operationName = operationName;
        this.query = query;
        this.key = key == null ? "" : key;
    }

    public QueryPayload(
            Map<String, Object> variables,
            Map<String, Object> extensions,
            String operationName,
            @Language("graphql") String query) {
        this(variables, extensions, operationName, query, "");
    }

    public QueryPayload(@Language("graphql") String query) {
        this(Map.of(), Map.of(), null, query, "");
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public String getOperationName() {
        return operationName;
    }

    public String getQuery() {
        return query;
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object other) {
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
        return Objects.hash(variables, extensions, operationName, query, key);
    }

    @Override
    public String toString() {
        return "QueryPayload(variables=" + variables + ", extensions=" + extensions
                + ", operationName=" + operationName + ", query=" + query + ", key=" + key + ")";
    }
}
