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

package com.netflix.graphql.dgs

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.TypeRef
import graphql.ExecutionResult
import org.springframework.http.HttpHeaders

/**
 * Represents the core query executing capability of the framework.
 * Use this interface to easily execute GraphQL queries, without using the HTTP endpoint.
 * This is meant to be used in tests, and is also used internally in the framework.
 *
 * The executeAnd* methods use the [JsonPath library](https://github.com/json-path/JsonPath) library to easily get specific fields out of a nested Json structure.
 * The [executeAndGetDocumentContext] method sets up a DocumentContext, which can then be reused to get multiple fields.
 *
 * See https://netflix.github.io/dgs/query-execution-testing/
 */
interface DgsQueryExecutor {
    fun execute(query: String): ExecutionResult = execute(query = query, variables = emptyMap())
    fun execute(query: String, variables: Map<String, Any>): ExecutionResult = execute(query = query, variables = variables, operationName = null)
    fun execute(query: String, variables: Map<String, Any> = mutableMapOf(), operationName: String? = null): ExecutionResult

    fun execute(query: String, variables: Map<String, Any>, extensions: Map<String, Any>?, headers: HttpHeaders): ExecutionResult =
            execute(query = query, variables = variables, extensions = extensions, headers = headers, operationName = null)
    fun execute(query: String, variables: Map<String, Any>, extensions: Map<String, Any>?, headers: HttpHeaders, operationName: String? = null): ExecutionResult

    fun <T> executeAndExtractJsonPath(query: String, jsonPath: String):T
    fun <T> executeAndExtractJsonPath(query: String, jsonPath: String, variables: Map<String, Any>):T

    fun executeAndGetDocumentContext(query: String): DocumentContext
    fun executeAndGetDocumentContext(query: String, variables: Map<String, Any>): DocumentContext

    fun <T> executeAndExtractJsonPathAsObject(query: String, jsonPath: String, clazz: Class<T>): T
    fun <T> executeAndExtractJsonPathAsObject(query: String, jsonPath: String, variables: Map<String, Any>, clazz: Class<T>): T
    fun <T> executeAndExtractJsonPathAsObject(query: String, jsonPath: String, typeRef: TypeRef<T>): T
    fun <T> executeAndExtractJsonPathAsObject(query: String, jsonPath: String, variables: Map<String, Any>, typeRef: TypeRef<T>): T
}
