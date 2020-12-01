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
 * See http://manuals.test.netflix.net/view/dgs/mkdocs/master/testing/
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
