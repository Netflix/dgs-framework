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
import com.jayway.jsonpath.TypeRef;

import java.util.List;
import java.util.Map;

/**
 * Jackson-agnostic view of a GraphQL response. Implementations parse the response body and provide access to
 * {@code data} and {@code errors}.
 */
public interface DgsGraphQLResponse {
    static String getDataPath(String path) {
        return GraphQLResponseSupport.dataPath(path);
    }

    String getJson();

    Map<String, List<String>> getHeaders();

    DocumentContext getParsed();

    Map<String, Object> getData();

    List<GraphQLError> getErrors();

    <T> T dataAsObject(Class<T> clazz);

    /**
     * Extract a value at {@code path}. Returns whatever type the caller binds to — for JSON objects
     * this is a Map. Use {@link #extractValueAsObject} to deserialize into a specific class instead.
     */
    default <T> T extractValue(String path) {
        String dataPath = getDataPath(path);
        try {
            return getParsed().read(dataPath);
        } catch (Exception ex) {
            GraphQLResponseSupport.logger.warn("Error extracting path '{}' from data: '{}'", path, getData());
            throw ex;
        }
    }

    default <T> T extractValueAsObject(String path, Class<T> clazz) {
        String dataPath = getDataPath(path);
        try {
            return getParsed().read(dataPath, clazz);
        } catch (Exception ex) {
            GraphQLResponseSupport.logger.warn("Error extracting path '{}' from data: '{}'", path, getData());
            throw ex;
        }
    }

    /** Use this overload for generic types like {@code List<Foo>}. */
    default <T> T extractValueAsObject(String path, TypeRef<T> typeRef) {
        String dataPath = getDataPath(path);
        try {
            return getParsed().read(dataPath, typeRef);
        } catch (Exception ex) {
            GraphQLResponseSupport.logger.warn("Error extracting path '{}' from data: '{}'", path, getData());
            throw ex;
        }
    }

    default RequestDetails getRequestDetails() {
        return extractValueAsObject("gatewayRequestDetails", RequestDetails.class);
    }

    default boolean hasErrors() {
        return !getErrors().isEmpty();
    }
}
