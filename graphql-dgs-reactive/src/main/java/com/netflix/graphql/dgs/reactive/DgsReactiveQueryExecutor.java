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

package com.netflix.graphql.dgs.reactive;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.TypeRef;
import graphql.ExecutionResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

public interface DgsReactiveQueryExecutor {
    /**
     * @param query The query string
     * @return Returns a GraphQL [ExecutionResult]. This includes data and errors.
     */
    default Mono<ExecutionResult> execute(String query) {
        return execute(query, null, null, null, null, null);
    }

    /**
     * @param query The query string
     * @param variables A map of variables https://graphql.org/learn/queries/#variables
     * @return Returns a GraphQL [ExecutionResult]. This includes data and errors.
     */
    default Mono<ExecutionResult> execute(String query, Map<String, Object> variables) {
        return execute(query, variables, null, null, null, null);
    }

    /**
     * @param query The query string
     * @param variables A map of variables https://graphql.org/learn/queries/#variables
     * @param operationName The operation name https://graphql.org/learn/queries/#operation-name
     * @return Returns a GraphQL [ExecutionResult]. This includes data and errors.
     */
    default Mono<ExecutionResult> execute(String query, Map<String, Object> variables, String operationName) {
        return execute(query, variables, null, null, operationName, null);
    }

    /**
     * @param query The query string
     * @param variables A map of variables https://graphql.org/learn/queries/#variables
     * @param extensions A map representing GraphQL extensions. This is made available in the [com.netflix.graphql.dgs.internal.DgsRequestData] object on [com.netflix.graphql.dgs.context.DgsContext].
     * @return Returns a GraphQL [ExecutionResult]. This includes data and errors.
     */
    default Mono<ExecutionResult> execute(String query, Map<String, Object> variables, Map<String,Object> extensions, HttpHeaders headers) {
        return execute(query, variables, extensions, headers, null, null);
    }

    /**
     * Executes a GraphQL query. This method is used internally by all other methods in this interface.
     * @param query The query string
     * @param variables A map of variables https://graphql.org/learn/queries/#variables
     * @param extensions A map representing GraphQL extensions. This is made available in the [com.netflix.graphql.dgs.internal.DgsRequestData] object on [com.netflix.graphql.dgs.context.DgsContext].
     * @param headers Request headers represented as a Spring Framework [HttpHeaders]
     * @param operationName Operation name https://graphql.org/learn/queries/#operation-name
     * @param serverHttpRequest A Spring [{@link ServerHttpRequest}] giving access to request details. Can cast to an environment specific class such as [org.springframework.web.context.request.ServletWebRequest].
     * @return Returns a GraphQL [ExecutionResult]. This includes data and errors.
     */
    Mono<ExecutionResult> execute(String query, Map<String, Object> variables, Map<String,Object> extensions, HttpHeaders headers, String operationName, ServerRequest serverHttpRequest);

    /**
     * Executes a GraphQL query, parses the returned data, and uses a Json Path to extract specific elements out of the data.
     * The method is generic, and tries to cast the result into the type you specify. This does NOT work work Lists. Use [executeAndExtractJsonPathAsObject] with a [TypeRef] instead.
     * @param query Query string
     * @param jsonPath JsonPath expression. See https://github.com/json-path/JsonPath for syntax.
     * @return T is the type you specify. This only works for primitive types and map representations. Use [executeAndExtractJsonPathAsObject] for complex types and lists.
     */
    default <T> Mono<T> executeAndExtractJsonPath(String query, String jsonPath) {
        return executeAndExtractJsonPath(query, jsonPath, Collections.emptyMap());
    }

    /**
     * Executes a GraphQL query, parses the returned data, and uses a Json Path to extract specific elements out of the data.
     * The method is generic, and tries to cast the result into the type you specify. This does NOT work work Lists. Use [executeAndExtractJsonPathAsObject] with a [TypeRef] instead.
     * @param query Query string
     * @param jsonPath JsonPath expression. See https://github.com/json-path/JsonPath for syntax.
     * @param variables A Map of variables https://graphql.org/learn/queries/#variables
     * @return T is the type you specify. This only works for primitive types and map representations. Use [executeAndExtractJsonPathAsObject] for complex types and lists.
     */
    <T> Mono<T> executeAndExtractJsonPath(String query, String jsonPath, Map<String, Object> variables);

    /**
     * Executes a GraphQL query, parses the returned data, and return a [DocumentContext].
     * A [DocumentContext] can be used to extract multiple values using JsonPath, without re-executing the query.
     * @param query Query string
     * @return [DocumentContext] is a JsonPath type used to extract values from.
     */
    default Mono<DocumentContext> executeAndGetDocumentContext(String query) {
        return executeAndGetDocumentContext(query, Collections.emptyMap());
    }

    /**
     * Executes a GraphQL query, parses the returned data, and return a [DocumentContext].
     * A [DocumentContext] can be used to extract multiple values using JsonPath, without re-executing the query.
     * @param query Query string
     * @param variables A Map of variables https://graphql.org/learn/queries/#variables
     * @return [DocumentContext] is a JsonPath type used to extract values from.
     */
    Mono<DocumentContext> executeAndGetDocumentContext(String query, Map<String, Object> variables);

    /**
     * Executes a GraphQL query, parses the returned data, extracts a value using JsonPath, and converts that value into the given type.
     * Be aware that this method can't guarantee type safety.
     * @param query Query string
     * @param jsonPath JsonPath expression. See https://github.com/json-path/JsonPath for syntax.
     * @param clazz The type to convert the extracted value to.
     * @return The extracted value from the result, converted to type T
     */
    default <T> Mono<T> executeAndExtractJsonPathAsObject(String query, String jsonPath, Class<T> clazz) {
        return executeAndExtractJsonPathAsObject(query, jsonPath, Collections.emptyMap(), clazz);
    }

    /**
     * Executes a GraphQL query, parses the returned data, extracts a value using JsonPath, and converts that value into the given type.
     * Be aware that this method can't guarantee type safety.
     * @param query Query string
     * @param jsonPath JsonPath expression. See https://github.com/json-path/JsonPath for syntax.
     * @param variables A Map of variables https://graphql.org/learn/queries/#variables
     * @param clazz The type to convert the extracted value to.
     * @return The extracted value from the result, converted to type T
     */
    <T> Mono<T> executeAndExtractJsonPathAsObject(String query, String jsonPath, Map<String, Object> variables, Class<T> clazz);

    /**
     * Executes a GraphQL query, parses the returned data, extracts a value using JsonPath, and converts that value into the given type.
     * Uses a [TypeRef] to specify the expected type, which is useful for Lists and Maps.
     * Details about [TypeRef] can be found in the JsonPath documentation: https://github.com/json-path/JsonPath#what-is-returned-when
     * Be aware that this method can't guarantee type safety.
     * @param query Query string
     * @param jsonPath JsonPath expression. See https://github.com/json-path/JsonPath for syntax.
     * @param typeRef A JsonPath [TypeRef] representing the expected result type.
     * @return The extracted value from the result, converted to type T
     */
    default <T> Mono<T> executeAndExtractJsonPathAsObject(String query, String jsonPath, TypeRef<T> typeRef) {
        return executeAndExtractJsonPathAsObject(query, jsonPath, Collections.emptyMap(), typeRef);
    }

    /**
     * Executes a GraphQL query, parses the returned data, extracts a value using JsonPath, and converts that value into the given type.
     * Uses a [TypeRef] to specify the expected type, which is useful for Lists and Maps.
     * Details about [TypeRef] can be found in the JsonPath documentation: https://github.com/json-path/JsonPath#what-is-returned-when
     * Be aware that this method can't guarantee type safety.
     * @param query Query string
     * @param jsonPath JsonPath expression. See https://github.com/json-path/JsonPath for syntax.
     * @param variables A Map of variables https://graphql.org/learn/queries/#variables
     * @param typeRef A JsonPath [TypeRef] representing the expected result type.
     * @return The extracted value from the result, converted to type T
     */
    <T> Mono<T> executeAndExtractJsonPathAsObject(String query, String jsonPath, Map<String, Object> variables, TypeRef<T> typeRef);
}
