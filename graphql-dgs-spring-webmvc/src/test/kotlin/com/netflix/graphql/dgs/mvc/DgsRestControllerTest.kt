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

import com.netflix.graphql.dgs.DgsQueryExecutor
import graphql.ExecutionResultImpl
import graphql.execution.reactive.SubscriptionPublisher
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.InstanceOfAssertFactories
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.context.request.WebRequest

@ExtendWith(MockKExtension::class)
class DgsRestControllerTest {

    private val httpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

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

        val result = DgsRestController(dgsQueryExecutor).graphql(requestBody.toByteArray(), null, null, null, httpHeaders, webRequest)

        assertThat(result.body).asInstanceOf(InstanceOfAssertFactories.map(String::class.java, Any::class.java))
            .doesNotContainKey("errors")
            .extracting("data").asInstanceOf(InstanceOfAssertFactories.map(String::class.java, Any::class.java))
            .extracting("hello").isEqualTo("hello")
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

        val result = DgsRestController(dgsQueryExecutor).graphql(requestBody.toByteArray(), null, null, null, httpHeaders, webRequest)

        assertThat(result.body).asInstanceOf(InstanceOfAssertFactories.map(String::class.java, Any::class.java))
            .doesNotContainKey("errors")
            .extracting("data").asInstanceOf(InstanceOfAssertFactories.map(String::class.java, Any::class.java))
            .extracting("hi").isEqualTo("there")

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
        val result = DgsRestController(dgsQueryExecutor).graphql(requestBody.toByteArray(), null, null, null, headers, webRequest)

        assertThat(result.body).asInstanceOf(InstanceOfAssertFactories.map(String::class.java, Any::class.java))
            .doesNotContainKey("errors")
            .extracting("data").asInstanceOf(InstanceOfAssertFactories.map(String::class.java, Any::class.java))
            .extracting("hello").isEqualTo("hello")
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
            DgsRestController(dgsQueryExecutor).graphql(requestBody.toByteArray(), null, null, null, httpHeaders, webRequest)
        assertThat(result.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(result.body).isEqualTo("Trying to execute subscription on /graphql. Use /subscriptions instead!")
    }

    @Test
    fun `Returns a request error if the no body is present`() {
        val requestBody = ""
        val result =
            DgsRestController(dgsQueryExecutor)
                .graphql(requestBody.toByteArray(), null, null, null, httpHeaders, webRequest)

        assertThat(result)
            .isInstanceOf(ResponseEntity::class.java)
            .extracting { it.statusCode }
            .isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `Writes response headers when dgs-response-headers are set in extensions object`() {
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
        } returns ExecutionResultImpl.newExecutionResult()
            .data(mapOf("hello" to "hello"))
            .extensions(mapOf(DgsRestController.DGS_RESPONSE_HEADERS_KEY to mapOf("myHeader" to "hello")))
            .build()

        val result = DgsRestController(dgsQueryExecutor).graphql(requestBody.toByteArray(), null, null, null, httpHeaders, webRequest)
        assertThat(result.headers["myHeader"]).contains("hello")

        assertThat(result.body).asInstanceOf(InstanceOfAssertFactories.map(String::class.java, Any::class.java))
            .doesNotContainKey("extensions")
    }

    @Test
    fun `Writes response headers when dgs-response-headers are set in extensions object with additional extensions`() {
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
        } returns ExecutionResultImpl.newExecutionResult()
            .data(mapOf("hello" to "hello"))
            .extensions(mapOf(DgsRestController.DGS_RESPONSE_HEADERS_KEY to mapOf("myHeader" to "hello"), "foo" to "bar"))
            .build()

        val result = DgsRestController(dgsQueryExecutor).graphql(requestBody.toByteArray(), null, null, null, httpHeaders, webRequest)
        assertThat(result.headers["myHeader"]).contains("hello")

        assertThat(result.body).asInstanceOf(InstanceOfAssertFactories.map(String::class.java, Any::class.java))
            .containsKey("extensions")
            .extracting("extensions").asInstanceOf(InstanceOfAssertFactories.map(String::class.java, Any::class.java))
            .containsEntry("foo", "bar")
    }
}
