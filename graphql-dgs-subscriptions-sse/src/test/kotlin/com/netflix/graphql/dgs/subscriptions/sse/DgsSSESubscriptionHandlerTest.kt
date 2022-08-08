/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.graphql.dgs.subscriptions.sse

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.types.subscription.QueryPayload
import com.netflix.graphql.types.subscription.SSEDataPayload
import graphql.ExecutionResultImpl
import graphql.GraphqlErrorBuilder
import graphql.validation.ValidationError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.any
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import reactor.core.publisher.Flux
import java.util.Base64

@WebMvcTest(DgsSSESubscriptionHandler::class, DgsSSESubscriptionHandlerTest.App::class)
internal class DgsSSESubscriptionHandlerTest {

    @SpringBootApplication
    open class App

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var dgsQueryExecutor: DgsQueryExecutor

    private val mapper: ObjectMapper = jacksonObjectMapper()

    @Test
    fun queryError() {
        val query = "subscription { stocks { name, price }}"
        val queryPayload = QueryPayload(operationName = "MySubscription", query = query)
        val encodedQuery = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(queryPayload))
        val executionResult = ExecutionResultImpl.newExecutionResult()
            .errors(listOf(GraphqlErrorBuilder.newError().message("broken").build()))
            .build()

        `when`(dgsQueryExecutor.execute(eq(query), any())).thenReturn(executionResult)

        mockMvc.perform(get("/subscriptions").param("query", encodedQuery))
            .andExpect(status().is4xxClientError)
    }

    @Test
    fun base64Error() {
        mockMvc.perform(get("/subscriptions").param("query", "notbase64"))
            .andExpect(status().is4xxClientError)
    }

    @Test
    fun queryValidationError() {
        val query = "subscription { stocks { name, price }}"
        val queryPayload = QueryPayload(operationName = "MySubscription", query = query)
        val encodedQuery = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(queryPayload))

        val executionResult = ExecutionResultImpl.newExecutionResult()
            .errors(listOf(ValidationError.newValidationError().build()))
            .build()

        `when`(dgsQueryExecutor.execute(eq(query), any())).thenReturn(executionResult)

        mockMvc.perform(get("/subscriptions").param("query", encodedQuery))
            .andExpect(status().is4xxClientError)
    }

    @Test
    fun invalidJson() {
        val encodedQuery = Base64.getEncoder().encodeToString("not json".toByteArray())

        mockMvc.perform(get("/subscriptions").param("query", encodedQuery))
            .andExpect(status().is4xxClientError)
    }

    @Test
    fun notAPublisherServerError() {
        val query = "subscription { stocks { name, price }}"
        val queryPayload = QueryPayload(operationName = "MySubscription", query = query)
        val encodedQuery = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(queryPayload))

        val executionResult = ExecutionResultImpl.newExecutionResult()
            .data("not a publisher")
            .build()

        `when`(dgsQueryExecutor.execute(eq(query), any())).thenReturn(executionResult)

        mockMvc.perform(get("/subscriptions").param("query", encodedQuery))
            .andExpect(status().is5xxServerError)
    }

    @Test
    fun notAPublisherClientError() {
        // Not a subscription query
        val query = "query { stocks { name, price }}"
        val queryPayload = QueryPayload(operationName = "MySubscription", query = query)
        val encodedQuery = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(queryPayload))

        val executionResult = ExecutionResultImpl.newExecutionResult()
            .data(mapOf("stocks" to listOf(mapOf("name" to "VTI", "price" to 200))))
            .build()

        `when`(dgsQueryExecutor.execute(eq(query), any())).thenReturn(executionResult)

        mockMvc.perform(get("/subscriptions").param("query", encodedQuery))
            .andExpect(status().is4xxClientError)
    }

    @Test
    fun success() {
        val query = "subscription { stocks { name, price }}"
        val queryPayload = QueryPayload(operationName = "MySubscription", query = query)
        val encodedQuery = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(queryPayload))

        val publisher = Flux.just(
            ExecutionResultImpl.newExecutionResult().data("message 1").build(),
            ExecutionResultImpl.newExecutionResult().data("message 2").build()
        )
        val executionResult = ExecutionResultImpl.newExecutionResult()
            .data(publisher).build()

        `when`(dgsQueryExecutor.execute(eq(query), any())).thenReturn(executionResult)

        val result = mockMvc.perform(get("/subscriptions").param("query", encodedQuery))
            .andExpect(request().asyncStarted())
            .andExpect(status().is2xxSuccessful)
            .andReturn()

        mockMvc.perform(asyncDispatch(result))
            .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM))
            .andReturn()

        val messages = result.response.contentAsString.lineSequence()
            .filter { line -> line.startsWith("data:") }
            .map { line -> line.substring("data:".length) }
            .map { line -> mapper.readValue<SSEDataPayload>(line) }
            .toList()

        assertEquals(2, messages.size)
        assertEquals("message 1", messages[0].data)
        assertEquals("message 2", messages[1].data)
    }
}
