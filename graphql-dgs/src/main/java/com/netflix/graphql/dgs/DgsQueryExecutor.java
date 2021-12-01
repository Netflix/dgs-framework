/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.graphql.dgs;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.TypeRef;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.WebRequest;

import java.util.Collections;
import java.util.Map;

/**
 * Represents the core query executing capability of the framework.
 * Use this interface to easily execute GraphQL queries, without using the HTTP endpoint.
 * This is meant to be used in tests, and is also used internally in the framework.
 *
 * The executeAnd* methods use the <a href="https://github.com/json-path/JsonPath">JsonPath library</a> library to easily get specific fields out of a nested Json structure.
 * The {@link #executeAndGetDocumentContext(String)} method sets up a DocumentContext, which can then be reused to get multiple fields.
 *
 * @see <a href="https://netflix.github.io/dgs/query-execution-testing/">Query Execution Testing docs</a>
 */
public interface DgsQueryExecutor {
    /**
     * @param query The query string
     * @return Returns a GraphQL {@link ExecutionResult}. This includes data and errors.
     */
    default ExecutionResult execute(String query) {
        return execute(query, Collections.emptyMap(), null, null ,null, null);
    }

    /**
     * @param query The query string
     * @param variables A map of variables
     * @return Returns a GraphQL {@link ExecutionResult}. This includes data and errors.
     * @see <a href="https://graphql.org/learn/queries/#variables">Query Variables</a>
     */
    default ExecutionResult execute(String query, Map<String, Object> variables) {
        return execute(query, variables, null, null ,null, null);
    }

    /**
     * @param query The query string
     * @param variables A map of variables
     * @param operationName The operation name
     * @return Returns a GraphQL {@link ExecutionResult}. This includes data and errors.
     * @see <a href="https://graphql.org/learn/queries/#variables">Query Variables</a>
     * @see <a href="https://graphql.org/learn/queries/#operation-name">Operation name</a>
     */
    default ExecutionResult execute(String query, Map<String, Object> variables, String operationName) {
        return execute(query, variables, null, null ,operationName, null);
    }

    /**
     * @param query The query string
     * @param variables A map of variables
     * @param extensions A map representing GraphQL extensions. This is made available in the {@link com.netflix.graphql.dgs.internal.DgsRequestData} object on {@link }com.netflix.graphql.dgs.context.DgsContext}.
     * @return Returns a GraphQL {@link ExecutionResult}. This includes data and errors.
     * @see <a href="https://graphql.org/learn/queries/#variables">Query Variables</a>
     */
    default ExecutionResult execute(String query, Map<String, Object> variables, Map<String,Object> extensions, HttpHeaders headers) {
        return execute(query, variables, extensions, headers ,null, null);
    }

    /**
     * Executes a GraphQL query. This method is used internally by all other methods in this interface.
     * @param query The query string
     * @param variables A map of variables
     * @param extensions A map representing GraphQL extensions. This is made available in the {@link com.netflix.graphql.dgs.internal.DgsRequestData} object on {@link com.netflix.graphql.dgs.context.DgsContext}.
     * @param headers Request headers represented as a Spring Framework {@link HttpHeaders}
     * @param operationName Operation name
     * @param webRequest A Spring {@link WebRequest} giving access to request details. Can cast to an environment specific class such as {@link org.springframework.web.context.request.ServletWebRequest}.
     * @return Returns a GraphQL {@link ExecutionResult}. This includes data and errors.
     * @see <a href="https://graphql.org/learn/queries/#variables">Query Variables</a>
     * @see <a href="https://graphql.org/learn/queries/#operation-name">Operation name</a>
     */
    ExecutionResult execute(String query, Map<String, Object> variables, Map<String,Object> extensions, HttpHeaders headers, String operationName, WebRequest webRequest);

    /**
     * Executes a GraphQL query, parses the returned data, and uses a Json Path to extract specific elements out of the data.
     * The method is generic, and tries to cast the result into the type you specify. This does NOT work on Lists. Use {@link #executeAndExtractJsonPathAsObject(String, String, TypeRef)}instead.
     * @param query Query string
     * @param jsonPath JsonPath expression.
     * @return T is the type you specify. This only works for primitive types and map representations. Use {@link #executeAndExtractJsonPathAsObject(String, String, Class)} for complex types and lists.
     * @see <a href="https://github.com/json-path/JsonPath">JsonPath syntax docs</a>
     */
    default <T> T executeAndExtractJsonPath(String query, String jsonPath) {
        return executeAndExtractJsonPath(query, jsonPath,  Collections.emptyMap());
    }

    /**
     * Executes a GraphQL query, parses the returned data, and uses a Json Path to extract specific elements out of the data.
     * The method is generic, and tries to cast the result into the type you specify. This does NOT work on Lists. Use {@link #executeAndExtractJsonPathAsObject(String, String, TypeRef)}instead.
     * @param query Query string
     * @param jsonPath JsonPath expression.
     * @param variables A Map of variables
     * @return T is the type you specify. This only works for primitive types and map representations. Use {@link #executeAndExtractJsonPathAsObject(String, String, Class)} for complex types and lists.
     * @see <a href="https://graphql.org/learn/queries/#variables">Query Variables</a>
     * @see <a href="https://github.com/json-path/JsonPath">JsonPath syntax docs</a>
     */
    <T> T executeAndExtractJsonPath(String query, String jsonPath, Map<String, Object> variables);

    /**
     * Executes a GraphQL query, parses the returned data, and uses a Json Path to extract specific elements out of the data.
     * The method is generic, and tries to cast the result into the type you specify. This does NOT work on Lists. Use {@link #executeAndExtractJsonPathAsObject(String, String, TypeRef)}instead.
     * @param query Query string
     * @param jsonPath JsonPath expression.
     * @param headers Spring {@link HttpHeaders}
     * @return T is the type you specify. This only works for primitive types and map representations. Use {@link #executeAndExtractJsonPathAsObject(String, String, Class)} for complex types and lists.
     * @see <a href="https://github.com/json-path/JsonPath">JsonPath syntax docs</a>
     */
    <T> T executeAndExtractJsonPath(String query, String jsonPath, HttpHeaders headers);

    /**
     * Executes a GraphQL query, parses the returned data, and return a {@link DocumentContext}.
     * A {@link DocumentContext} can be used to extract multiple values using JsonPath, without re-executing the query.
     * @param query Query string
     * @return {@link DocumentContext} is a JsonPath type used to extract values from.
     */
    default DocumentContext executeAndGetDocumentContext(String query) {
        return executeAndGetDocumentContext(query, Collections.emptyMap());
    }

    /**
     * Executes a GraphQL query, parses the returned data, and return a {@link DocumentContext}.
     * A {@link DocumentContext} can be used to extract multiple values using JsonPath, without re-executing the query.
     * @param query Query string
     * @param variables A Map of variables
     * @return {@link DocumentContext} is a JsonPath type used to extract values from.
     * @see <a href="https://graphql.org/learn/queries/#variables">Query Variables</a>
     */
    DocumentContext executeAndGetDocumentContext(String query, Map<String, Object> variables);

    /**
     * Executes a GraphQL query, parses the returned data, and return a {@link DocumentContext}.
     * A {@link DocumentContext} can be used to extract multiple values using JsonPath, without re-executing the query.
     * @param query Query string
     * @param variables A Map of variables
     * @param headers Spring {@link HttpHeaders}
     * @return {@link DocumentContext} is a JsonPath type used to extract values from.
     * @see <a href="https://graphql.org/learn/queries/#variables">Query Variables</a>
     */
    DocumentContext executeAndGetDocumentContext(String query, Map<String, Object> variables, HttpHeaders headers);

    /**
     * Executes a GraphQL query, parses the returned data, extracts a value using JsonPath, and converts that value into the given type.
     * Be aware that this method can't guarantee type safety.
     * @param query Query string
     * @param jsonPath JsonPath expression.
     * @param clazz The type to convert the extracted value to.
     * @return The extracted value from the result, converted to type T
     * @see <a href="https://github.com/json-path/JsonPath">JsonPath syntax docs</a>
     */
    default <T> T executeAndExtractJsonPathAsObject(String query, String jsonPath, Class<T> clazz) {
        return executeAndExtractJsonPathAsObject(query, jsonPath,  Collections.emptyMap(), clazz, null);
    }

    /**
     * Executes a GraphQL query, parses the returned data, extracts a value using JsonPath, and converts that value into the given type.
     * Be aware that this method can't guarantee type safety.
     * @param query Query string
     * @param jsonPath JsonPath expression.
     * @param variables A Map of variables
     * @param clazz The type to convert the extracted value to.
     * @return The extracted value from the result, converted to type T
     * @see <a href="https://github.com/json-path/JsonPath">JsonPath syntax docs</a>
     * @see <a href="https://graphql.org/learn/queries/#variables">Query Variables</a>
     */
    default <T> T executeAndExtractJsonPathAsObject(String query, String jsonPath, Map<String, Object> variables, Class<T> clazz) {
        return executeAndExtractJsonPathAsObject(query, jsonPath, variables, clazz, null);
    }

    /**
     * Executes a GraphQL query, parses the returned data, extracts a value using JsonPath, and converts that value into the given type.
     * Be aware that this method can't guarantee type safety.
     * @param query Query string
     * @param jsonPath JsonPath expression.
     * @param variables A Map of variables
     * @param clazz The type to convert the extracted value to.
     * @param headers Request headers represented as a Spring Framework {@link HttpHeaders}
     * @return The extracted value from the result, converted to type T
     * @see <a href="https://github.com/json-path/JsonPath">JsonPath syntax docs</a>
     * @see <a href="https://graphql.org/learn/queries/#variables">Query Variables</a>
     */
    <T> T executeAndExtractJsonPathAsObject(String query, String jsonPath, Map<String, Object> variables, Class<T> clazz, HttpHeaders headers);

    /**
     * Executes a GraphQL query, parses the returned data, extracts a value using JsonPath, and converts that value into the given type.
     * Uses a {@link TypeRef} to specify the expected type, which is useful for Lists and Maps.
     * Be aware that this method can't guarantee type safety.
     * @param query Query string
     * @param jsonPath JsonPath expression.
     * @param typeRef A JsonPath [TypeRef] representing the expected result type.
     * @return The extracted value from the result, converted to type T
     * @see <a href="https://github.com/json-path/JsonPath">JsonPath syntax docs</a>
     * @see <a href="https://github.com/json-path/JsonPath#what-is-returned-when">Using TypeRef</a>
     */
    default <T> T executeAndExtractJsonPathAsObject(String query, String jsonPath, TypeRef<T> typeRef) {
        return executeAndExtractJsonPathAsObject(query, jsonPath,  Collections.emptyMap(), typeRef, null);
    }

    /**
     * Executes a GraphQL query, parses the returned data, extracts a value using JsonPath, and converts that value into the given type.
     * Uses a {@link TypeRef} to specify the expected type, which is useful for Lists and Maps.
     * Be aware that this method can't guarantee type safety.
     * @param query Query string
     * @param jsonPath JsonPath expression.
     * @param variables A Map of variables
     * @param typeRef A JsonPath {@link TypeRef} representing the expected result type.
     * @return The extracted value from the result, converted to type T
     * @see <a href="https://github.com/json-path/JsonPath">JsonPath syntax docs</a>
     * @see <a href="https://graphql.org/learn/queries/#variables">Query Variables</a>
     * @see <a href="https://github.com/json-path/JsonPath#what-is-returned-when">Using TypeRef</a>
     */
    default <T> T executeAndExtractJsonPathAsObject(String query, String jsonPath, Map<String, Object> variables, TypeRef<T> typeRef) {
        return executeAndExtractJsonPathAsObject(query, jsonPath,  variables, typeRef, null);
    }

    /**
     * Executes a GraphQL query, parses the returned data, extracts a value using JsonPath, and converts that value into the given type.
     * Uses a {@link TypeRef} to specify the expected type, which is useful for Lists and Maps.
     * Be aware that this method can't guarantee type safety.
     * @param query Query string
     * @param jsonPath JsonPath expression.
     * @param variables A Map of variables
     * @param typeRef A JsonPath {@link TypeRef} representing the expected result type.
     * @param headers Request headers represented as a Spring Framework {@link HttpHeaders}
     * @return The extracted value from the result, converted to type T
     * @see <a href="https://github.com/json-path/JsonPath">JsonPath syntax docs</a>
     * @see <a href="https://graphql.org/learn/queries/#variables">Query Variables</a>
     * @see <a href="https://github.com/json-path/JsonPath#what-is-returned-when">Using TypeRef</a>
     */
    <T> T executeAndExtractJsonPathAsObject(String query, String jsonPath, Map<String, Object> variables, TypeRef<T> typeRef, HttpHeaders headers);

    GraphQL graphQL();
}
