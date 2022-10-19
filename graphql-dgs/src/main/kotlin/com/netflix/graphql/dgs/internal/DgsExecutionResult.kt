/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.graphql.dgs.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.internal.utils.TimeTracer
import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

class DgsExecutionResult @JvmOverloads constructor(
    val executionResult: ExecutionResult,
    val headers: HttpHeaders = HttpHeaders(),
    val status: HttpStatus = HttpStatus.OK
) : ExecutionResult by executionResult {
    companion object {
        private val sentinelObject = Any()
        private val logger: Logger = LoggerFactory.getLogger(DgsExecutionResult::class.java)
    }

    constructor(
        status: HttpStatus = HttpStatus.OK,
        headers: HttpHeaders = HttpHeaders.EMPTY,
        errors: List<GraphQLError> = listOf(),
        extensions: Map<Any, Any>? = null,

        // By default, assign data as a sentinel object.
        // If we were to default to null here, this constructor
        // would be unable to discriminate between an intentionally null
        // response and one that the user left default.
        data: Any? = sentinelObject
    ) : this(
        headers = headers,
        status = status,
        executionResult = ExecutionResultImpl
            .newExecutionResult()
            .errors(errors)
            .extensions(extensions)
            .apply {
                if (data != sentinelObject) {
                    data(data)
                }
            }
            .build()
    )

    fun toSpringResponse(
        mapper: ObjectMapper = jacksonObjectMapper()
    ): ResponseEntity<Any> {
        val result = try {
            TimeTracer.logTime(
                { mapper.writeValueAsBytes(this.toSpecification()) },
                logger,
                "Serialized JSON result in {}ms"
            )
        } catch (ex: InvalidDefinitionException) {
            val errorMessage = "Error serializing response: ${ex.message}"
            val errorResponse = ExecutionResultImpl(GraphqlErrorBuilder.newError().message(errorMessage).build())
            logger.error(errorMessage, ex)
            mapper.writeValueAsBytes(errorResponse.toSpecification())
        }

        return ResponseEntity(
            result,
            headers,
            status
        )
    }
}
