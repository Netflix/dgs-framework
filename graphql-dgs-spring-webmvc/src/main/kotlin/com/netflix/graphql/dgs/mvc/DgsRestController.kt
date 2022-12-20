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

package com.netflix.graphql.dgs.mvc

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.graphql.dgs.DgsExecutionResult
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.internal.utils.MultipartVariableMapper
import com.netflix.graphql.dgs.internal.utils.TimeTracer
import graphql.execution.reactive.SubscriptionPublisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.WebRequest
import org.springframework.web.multipart.MultipartFile

/**
 * HTTP entrypoint for the framework. Functionality in this class should be limited, so that as much code as possible
 * is reused between different transport protocols and the testing framework.
 *
 * In addition to regular graphql queries, this method also handles multipart POST requests containing files for upload.
 * This is usually a POST request that  has Content type set to multipart/form-data. Here is an example command.
 *
 * Each part in a multipart request is identified by the -F and is identified by the part name - "operations, map etc."
 * The "operations" part is the graphql query containing the mutation for the file upload, with variables for files set to null.
 * The "map" part and the subsequent parts specify the path of the file in the variables of the query, and will get mapped to
 * construct the graphql query that looks like this:
 *
 * {"query": "mutation ($input: FileUploadInput!) { uploadFile(input: $input) }",
 * "variables": { "input": { "description": "test", "files": [file1.txt, file2.txt] } }
 *
 * where files map to one or more MultipartFile(s)
 *
 * The remaining parts in the request contain the mapping of file name to file path, i.e. a map of MultipartFile(s)
 * The format of a multipart request is also described here:
 * https://github.com/jaydenseric/graphql-multipart-request-spec
 *
 * This class is defined as "open" only for proxy/aop use cases. It is not considered part of the API, and backwards compatibility is not guaranteed.
 * Do not manually extend this class.
 */

@RestController
open class DgsRestController(
    open val dgsQueryExecutor: DgsQueryExecutor,
    open val mapper: ObjectMapper = jacksonObjectMapper(),
    open val dgsGraphQLRequestHeaderValidator: DgsGraphQLRequestHeaderValidator = DefaultDgsGraphQLRequestHeaderValidator()
) {

    companion object {
        // defined in here and DgsExecutionResult, for backwards compatibility.
        // keep these two variables synced.
        const val DGS_RESPONSE_HEADERS_KEY = DgsExecutionResult.DGS_RESPONSE_HEADERS_KEY
        private val logger: Logger = LoggerFactory.getLogger(DgsRestController::class.java)
    }

    // The @ConfigurationProperties bean name is <prefix>-<fqn>
    // TODO Allow users to disable multipart-form/data
    @RequestMapping(
        "#{@'dgs.graphql-com.netflix.graphql.dgs.webmvc.autoconfigure.DgsWebMvcConfigurationProperties'.path}",
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun graphql(
        @RequestBody body: ByteArray?,
        @RequestParam fileParams: Map<String, MultipartFile>?,
        @RequestParam(name = "operations") operation: String?,
        @RequestParam(name = "map") mapParam: String?,
        @RequestHeader headers: HttpHeaders,
        webRequest: WebRequest
    ): ResponseEntity<Any> {
        logger.debug("Validate HTTP Headers for the GraphQL endpoint...")
        try {
            dgsGraphQLRequestHeaderValidator.assert(headers)
        } catch (e: DgsGraphQLRequestHeaderValidator.GraphqlRequestContentTypePredicateException) {
            logger.debug("Unsupported Media-Type {}.", headers.contentType, e)
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body("Unsupported media type.")
        } catch (e: DgsGraphQLRequestHeaderValidator.GraphQLRequestHeaderRuleException) {
            logger.debug("The Request Headers failed a DGS Header validation rule.", e)
            return ResponseEntity.badRequest().body(e.message)
        } catch (e: DgsGraphQLRequestHeaderValidator.GraphqlRequestHeaderValidationException) {
            logger.debug("The DGS Request Header Validator deemed the request headers as invalid.", e)
            return ResponseEntity.badRequest().body(e.message)
        } catch (e: Exception) {
            logger.error("The DGS Request Header Validator failed with exception!", e)
            return ResponseEntity.internalServerError().body("Unable to validate the HTTP Request Headers.")
        }

        logger.debug("Starting HTTP GraphQL handling...")

        val inputQuery: Map<String, Any>
        val queryVariables: Map<String, Any>
        val extensions: Map<String, Any>
        if (body != null) {
            if (logger.isDebugEnabled) {
                logger.debug("Reading input value: '{}'", body.decodeToString())
            }
            if (GraphQLMediaTypes.includesApplicationGraphQL(headers)) {
                inputQuery = mapOf("query" to body.decodeToString())
                queryVariables = emptyMap()
                extensions = emptyMap()
            } else {
                try {
                    inputQuery = mapper.readValue(body)
                } catch (ex: Exception) {
                    return when (ex) {
                        is JsonParseException ->
                            ResponseEntity.badRequest()
                                .body("Invalid query - ${ex.message ?: "no details found in the error message"}.")
                        is MismatchedInputException ->
                            ResponseEntity.badRequest()
                                .body("Invalid query - No content to map to input.")
                        else ->
                            ResponseEntity.badRequest()
                                .body("Invalid query - ${ex.message ?: "no additional details found"}.")
                    }
                }

                queryVariables = if (inputQuery["variables"] != null) {
                    @Suppress("UNCHECKED_CAST")
                    inputQuery["variables"] as Map<String, String>
                } else {
                    emptyMap()
                }

                extensions = if (inputQuery["extensions"] != null) {
                    @Suppress("UNCHECKED_CAST")
                    inputQuery["extensions"] as Map<String, Any>
                } else {
                    emptyMap()
                }

                logger.debug("Parsed variables: {}", queryVariables)
            }
        } else if (fileParams != null && mapParam != null && operation != null) {
            inputQuery = mapper.readValue(operation)

            queryVariables = if (inputQuery["variables"] != null) {
                @Suppress("UNCHECKED_CAST")
                inputQuery["variables"] as Map<String, Any>
            } else {
                emptyMap()
            }

            extensions = if (inputQuery["extensions"] != null) {
                @Suppress("UNCHECKED_CAST")
                inputQuery["extensions"] as Map<String, Any>
            } else {
                emptyMap()
            }

            // parse the '-F map' of MultipartFile(s) containing object paths
            val fileMapInput: Map<String, List<String>> = mapper.readValue(mapParam)
            fileMapInput.forEach { (fileKey, objectPaths) ->
                val file = fileParams[fileKey]
                if (file != null) {
                    // the variable mapper takes each multipart file and replaces the null portion of the query variables with the file
                    objectPaths.forEach { objectPath ->
                        MultipartVariableMapper.mapVariable(
                            objectPath,
                            queryVariables,
                            file
                        )
                    }
                }
            }
        } else {
            return ResponseEntity.badRequest().body("Invalid GraphQL request - no request body was provided")
        }

        val opName = inputQuery["operationName"]
        val gqlOperationName = if (opName is String?) {
            opName
        } else {
            return ResponseEntity.badRequest().body("Invalid GraphQL request - operationName must be a String")
        }

        val query: String? = inputQuery["query"] as? String?

        val executionResult = TimeTracer.logTime(
            {
                dgsQueryExecutor.execute(
                    query,
                    queryVariables,
                    extensions,
                    headers,
                    gqlOperationName,
                    webRequest
                )
            },
            logger,
            "Executed query in {}ms"
        )
        logger.debug(
            "Execution result - Contains data: '{}' - Number of errors: {}",
            executionResult.isDataPresent,
            executionResult.errors.size
        )

        if (executionResult.isDataPresent && executionResult.getData<Any>() is SubscriptionPublisher) {
            return ResponseEntity.badRequest()
                .body("Trying to execute subscription on /graphql. Use /subscriptions instead!")
        }

        return when (executionResult) {
            is DgsExecutionResult -> executionResult.toSpringResponse(mapper)
            else -> DgsExecutionResult.builder().executionResult(executionResult).build().toSpringResponse(mapper)
        }
    }
}
