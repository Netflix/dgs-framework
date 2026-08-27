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

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.json.DgsJsonMapper;
import org.intellij.lang.annotations.Language;

import java.util.List;
import java.util.Map;

public class DefaultDgsGraphQLResponse implements DgsGraphQLResponse {
    private static final TypeRef<List<GraphQLError>> ERRORS_TYPE = new TypeRef<>() {};

    private final String json;
    private final Map<String, List<String>> headers;
    private final DgsJsonMapper mapper;
    private final DocumentContext parsed;
    private final Map<String, Object> data;
    private final List<GraphQLError> errors;

    public DefaultDgsGraphQLResponse(
            @Language("json") String json, Map<String, List<String>> headers, DgsJsonMapper mapper) {
        this.json = json;
        this.headers = headers;
        this.mapper = mapper;
        this.parsed = JsonPath.using(mapper.jsonPathConfiguration()).parse(json);

        Map<String, Object> readData = parsed.read("data");
        this.data = readData != null ? readData : Map.of();
        List<GraphQLError> readErrors = parsed.read("errors", ERRORS_TYPE);
        this.errors = readErrors != null ? readErrors : List.of();
    }

    @Override
    public String getJson() {
        return json;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    @Override
    public DocumentContext getParsed() {
        return parsed;
    }

    @Override
    public Map<String, Object> getData() {
        return data;
    }

    @Override
    public List<GraphQLError> getErrors() {
        return errors;
    }

    @Override
    public <T> T dataAsObject(Class<T> clazz) {
        return mapper.convertValue(data, clazz);
    }
}
