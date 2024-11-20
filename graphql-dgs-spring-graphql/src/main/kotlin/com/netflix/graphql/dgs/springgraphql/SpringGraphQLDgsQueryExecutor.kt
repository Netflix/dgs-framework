/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.graphql.dgs.springgraphql

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.mapper.MappingException
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.context.GraphQLContextContributor
import com.netflix.graphql.dgs.exceptions.DgsQueryExecutionDataExtractionException
import com.netflix.graphql.dgs.exceptions.QueryException
import com.netflix.graphql.dgs.internal.BaseDgsQueryExecutor
import com.netflix.graphql.dgs.internal.DefaultDgsGraphQLContextBuilder
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsQueryExecutorRequestCustomizer
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData
import graphql.ExecutionResult
import graphql.GraphQLContext
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.support.DefaultExecutionGraphQlRequest
import org.springframework.http.HttpHeaders
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest

class SpringGraphQLDgsQueryExecutor(
    private val executionService: ExecutionGraphQlService,
    private val dgsContextBuilder: DefaultDgsGraphQLContextBuilder,
    private val dgsDataLoaderProvider: DgsDataLoaderProvider,
    private val requestCustomizer: DgsQueryExecutorRequestCustomizer = DgsQueryExecutorRequestCustomizer.DEFAULT_REQUEST_CUSTOMIZER,
    private val graphQLContextContributors: List<GraphQLContextContributor>,
) : DgsQueryExecutor {
    override fun execute(
        query: String,
        variables: Map<String, Any>,
        extensions: Map<String, Any>?,
        headers: HttpHeaders?,
        operationName: String?,
        webRequest: WebRequest?,
    ): ExecutionResult {
        val request =
            DefaultExecutionGraphQlRequest(
                query,
                operationName,
                variables,
                extensions,
                "",
                null,
            )

        val httpRequest = requestCustomizer.apply(webRequest ?: RequestContextHolder.getRequestAttributes() as? WebRequest, headers)
        val dgsContext = dgsContextBuilder.build(DgsWebMvcRequestData(request.extensions, headers, httpRequest))
        val dataLoaderRegistry =
            dgsDataLoaderProvider.buildRegistryWithContextSupplier {
                val graphQLContext = request.toExecutionInput().graphQLContext
                if (graphQLContextContributors.isNotEmpty()) {
                    val requestData = dgsContext.requestData
                    val builderForContributors = GraphQLContext.newContext()
                    graphQLContextContributors.forEach { it.contribute(builderForContributors, extensions, requestData) }
                    graphQLContext.putAll(builderForContributors)
                }

                graphQLContext
            }

        request.configureExecutionInput { _, builder ->
            builder
                .context(dgsContext)
                .graphQLContext(dgsContext)
                .dataLoaderRegistry(dataLoaderRegistry)
                .build()
        }

        val response =
            executionService.execute(request).block() ?: throw IllegalStateException("Unexpected null response from Spring GraphQL client")

        return response.executionResult
    }

    override fun <T : Any?> executeAndExtractJsonPath(
        query: String,
        jsonPath: String,
        variables: MutableMap<String, Any>,
    ): T = JsonPath.read(getJsonResult(query, variables), jsonPath)

    override fun <T : Any?> executeAndExtractJsonPath(
        query: String,
        jsonPath: String,
        headers: HttpHeaders,
    ): T = JsonPath.read(getJsonResult(query, emptyMap(), headers), jsonPath)

    override fun <T : Any?> executeAndExtractJsonPath(
        query: String,
        jsonPath: String,
        servletWebRequest: ServletWebRequest,
    ): T {
        val httpHeaders = HttpHeaders()
        servletWebRequest.headerNames.forEach { name ->
            httpHeaders.addAll(name, servletWebRequest.getHeaderValues(name).orEmpty().toList())
        }

        return JsonPath.read(getJsonResult(query, emptyMap(), httpHeaders, servletWebRequest), jsonPath)
    }

    override fun executeAndGetDocumentContext(
        query: String,
        variables: MutableMap<String, Any>,
    ): DocumentContext = BaseDgsQueryExecutor.parseContext.parse(getJsonResult(query, variables))

    override fun executeAndGetDocumentContext(
        query: String,
        variables: MutableMap<String, Any>,
        headers: HttpHeaders?,
    ): DocumentContext = BaseDgsQueryExecutor.parseContext.parse(getJsonResult(query, variables, headers))

    override fun <T : Any?> executeAndExtractJsonPathAsObject(
        query: String,
        jsonPath: String,
        variables: MutableMap<String, Any>,
        clazz: Class<T>,
        headers: HttpHeaders?,
    ): T {
        val jsonResult = getJsonResult(query, variables, headers)
        return try {
            BaseDgsQueryExecutor.parseContext.parse(jsonResult).read(jsonPath, clazz)
        } catch (ex: MappingException) {
            throw DgsQueryExecutionDataExtractionException(ex, jsonResult, jsonPath, clazz)
        }
    }

    override fun <T : Any?> executeAndExtractJsonPathAsObject(
        query: String,
        jsonPath: String,
        variables: MutableMap<String, Any>,
        typeRef: TypeRef<T>,
        headers: HttpHeaders?,
    ): T {
        val jsonResult = getJsonResult(query, variables, headers)
        return try {
            BaseDgsQueryExecutor.parseContext.parse(jsonResult).read(jsonPath, typeRef)
        } catch (ex: MappingException) {
            throw DgsQueryExecutionDataExtractionException(ex, jsonResult, jsonPath, typeRef)
        }
    }

    private fun getJsonResult(
        query: String,
        variables: Map<String, Any>,
        headers: HttpHeaders? = null,
        servletWebRequest: ServletWebRequest? = null,
    ): String {
        val executionResult = execute(query, variables, null, headers, null, servletWebRequest)

        if (executionResult.errors.size > 0) {
            throw QueryException(executionResult.errors)
        }

        return BaseDgsQueryExecutor.objectMapper.writeValueAsString(executionResult.toSpecification())
    }
}
