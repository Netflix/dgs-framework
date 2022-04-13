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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import graphql.ExecutionResultImpl
import graphql.execution.reactive.SubscriptionPublisher
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.context.request.WebRequest

@ExtendWith(MockKExtension::class)
class DgsRestControllerTest {
    @MockK
    lateinit var dgsQueryExecutor: DgsQueryExecutor

    @MockK
    lateinit var webRequest: WebRequest

    @Test
    fun `Is able to execute a a well formed query`() {
        val queryString = "query { hello }"
        val requestBody = """
            {
                "query": "$queryString"
            }
        """.trimIndent()

        every {
            dgsQueryExecutor.execute(
                queryString,
                emptyMap(),
                any(),
                any(),
                any(),
                any()
            )
        } returns ExecutionResultImpl.newExecutionResult().data(mapOf(Pair("hello", "hello"))).build()

        val result =
            DgsRestController(dgsQueryExecutor).graphql(requestBody, null, null, null, HttpHeaders(), webRequest)
        val mapper = jacksonObjectMapper()
        val (data, errors) = mapper.readValue(result.body, GraphQLResponse::class.java)
        assertThat(errors.size).isEqualTo(0)
        assertThat(data["hello"]).isEqualTo("hello")
    }

    @Test
    fun `Passing a query with an operationName should execute the matching named query`() {
        val queryString = "query operationA{ hello } query operationB{ hi }"
        val requestBody = """
            {
                "query": "$queryString",
                "operationName": "operationB"
            }
        """.trimIndent()

        val capturedOperationName = slot<String>()
        every {
            dgsQueryExecutor.execute(
                queryString,
                emptyMap(),
                any(),
                any(),
                capture(capturedOperationName),
                any()
            )
        } returns ExecutionResultImpl.newExecutionResult().data(mapOf(Pair("hi", "there"))).build()

        val result =
            DgsRestController(dgsQueryExecutor).graphql(requestBody, null, null, null, HttpHeaders(), webRequest)
        val mapper = jacksonObjectMapper()
        val (data, errors) = mapper.readValue(result.body, GraphQLResponse::class.java)
        assertThat(errors.size).isEqualTo(0)
        assertThat(data["hi"]).isEqualTo("there")

        assertThat(capturedOperationName.captured).isEqualTo("operationB")
    }

    @Test
    fun `Content-type application graphql should be handled correctly`() {
        val requestBody = """
            {
               hello
            }
        """.trimIndent()

        every {
            dgsQueryExecutor.execute(
                requestBody,
                emptyMap(),
                any(),
                any(),
                any(),
                any()
            )
        } returns ExecutionResultImpl.newExecutionResult().data(mapOf(Pair("hello", "hello"))).build()

        val headers = HttpHeaders()
        headers.contentType = MediaType("application", "graphql")
        val result = DgsRestController(dgsQueryExecutor).graphql(requestBody, null, null, null, headers, webRequest)
        val mapper = jacksonObjectMapper()
        val (data, errors) = mapper.readValue(result.body, GraphQLResponse::class.java)
        assertThat(errors.size).isEqualTo(0)
        assertThat(data["hello"]).isEqualTo("hello")
    }

    @Test
    fun `Return an error when a Subscription is attempted on the Graphql Endpoint`() {
        val queryString = "subscription { stocks { name } }"
        val requestBody = """
            {
                "query": "$queryString"
            }
        """.trimIndent()

        every {
            dgsQueryExecutor.execute(
                queryString,
                emptyMap(),
                any(),
                any(),
                any(),
                any()
            )
        } returns ExecutionResultImpl.newExecutionResult()
            .data(SubscriptionPublisher(null, null)).build()

        val result =
            DgsRestController(dgsQueryExecutor).graphql(requestBody, null, null, null, HttpHeaders(), webRequest)
        assertThat(result.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(result.body).isEqualTo("Trying to execute subscription on /graphql. Use /subscriptions instead!")
    }

    @Test
    fun `Returns a request error if the no body is present`() {
        val requestBody = ""
        val result =
            DgsRestController(dgsQueryExecutor)
                .graphql(requestBody, null, null, null, HttpHeaders(), webRequest)

        assertThat(result)
            .isInstanceOf(ResponseEntity::class.java)
            .extracting { it.statusCode }
            .isEqualTo(HttpStatus.BAD_REQUEST)
    }

    data class GraphQLResponse(val data: Map<String, Any> = emptyMap(), val errors: List<GraphQLError> = emptyList())

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GraphQLError(val message: String)
}
