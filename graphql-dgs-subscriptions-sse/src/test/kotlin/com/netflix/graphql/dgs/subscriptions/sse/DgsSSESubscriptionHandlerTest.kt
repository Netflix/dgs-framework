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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.types.subscription.QueryPayload
import graphql.ExecutionResult
import graphql.GraphqlErrorBuilder
import graphql.validation.ValidationError
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import java.util.*

@ExtendWith(MockKExtension::class)
internal class DgsSSESubscriptionHandlerTest {

    @MockK
    lateinit var dgsQueryExecutor: DgsQueryExecutor

    @MockK
    lateinit var executionResultMock: ExecutionResult

    @Test
    fun queryError() {

        val query = "subscription { stocks { name, price }}"
        val queryPayload = QueryPayload(operationName = "MySubscription", query = query)
        val base64 = Base64.getEncoder().encodeToString(jacksonObjectMapper().writeValueAsBytes(queryPayload))

        every { dgsQueryExecutor.execute(query, any()) } returns executionResultMock
        every { executionResultMock.errors } returns listOf(GraphqlErrorBuilder.newError().message("broken").build())

        val responseEntity = DgsSSESubscriptionHandler(dgsQueryExecutor).subscriptionWithId(base64)
        assertThat(responseEntity.statusCode.is5xxServerError).isTrue
    }

    @Test
    fun base64Error() {

        val query = "subscription { stocks { name, price }}"
        val base64 = "notbase64"

        every { dgsQueryExecutor.execute(query, any()) } returns executionResultMock

        val responseEntity = DgsSSESubscriptionHandler(dgsQueryExecutor).subscriptionWithId(base64)
        assertThat(responseEntity.statusCode.is4xxClientError).isTrue
    }

    @Test
    fun queryValidationError() {

        val query = "subscription { stocks { name, price }}"
        val queryPayload = QueryPayload(operationName = "MySubscription", query = query)
        val base64 = Base64.getEncoder().encodeToString(jacksonObjectMapper().writeValueAsBytes(queryPayload))

        every { dgsQueryExecutor.execute(query, any()) } returns executionResultMock
        every { executionResultMock.errors } returns listOf(ValidationError.newValidationError().build())

        val responseEntity = DgsSSESubscriptionHandler(dgsQueryExecutor).subscriptionWithId(base64)
        assertThat(responseEntity.statusCode.is4xxClientError).isTrue
    }

    @Test
    fun invalidJson() {

        val query = "subscription { stocks { name, price }}"
        val base64 = Base64.getEncoder().encodeToString("not json".toByteArray())
        every { dgsQueryExecutor.execute(query, any()) } returns executionResultMock
        val responseEntity = DgsSSESubscriptionHandler(dgsQueryExecutor).subscriptionWithId(base64)
        assertThat(responseEntity.statusCode.is4xxClientError).isTrue
    }

    @Test
    fun notAPublisherServerError() {

        val query = "subscription { stocks { name, price }}"
        val queryPayload = QueryPayload(operationName = "MySubscription", query = query)
        val base64 = Base64.getEncoder().encodeToString(jacksonObjectMapper().writeValueAsBytes(queryPayload))

        every { dgsQueryExecutor.execute(query, any()) } returns executionResultMock
        every { executionResultMock.errors } returns emptyList()
        every { executionResultMock.getData<Publisher<ExecutionResult>>() } throws ClassCastException()

        val responseEntity = DgsSSESubscriptionHandler(dgsQueryExecutor).subscriptionWithId(base64)
        assertThat(responseEntity.statusCode.is5xxServerError).isTrue
    }

    @Test
    fun notAPublisherClientError() {
        // Not a subscription query
        val query = "query { stocks { name, price }}"
        val queryPayload = QueryPayload(operationName = "MySubscription", query = query)
        val base64 = Base64.getEncoder().encodeToString(jacksonObjectMapper().writeValueAsBytes(queryPayload))

        every { dgsQueryExecutor.execute(query, any()) } returns executionResultMock
        every { executionResultMock.errors } returns emptyList()
        every { executionResultMock.getData<Publisher<ExecutionResult>>() } throws ClassCastException()

        val responseEntity = DgsSSESubscriptionHandler(dgsQueryExecutor).subscriptionWithId(base64)
        assertThat(responseEntity.statusCode.is4xxClientError).isTrue
    }

    @Test
    @Suppress("ReactiveStreamsUnusedPublisher")
    fun success() {
        val query = "query { stocks { name, price }}"
        val queryPayload = QueryPayload(operationName = "MySubscription", query = query)
        val base64 = Base64.getEncoder().encodeToString(jacksonObjectMapper().writeValueAsBytes(queryPayload))

        val nestedExecutionResult = mockk<ExecutionResult>()

        every { dgsQueryExecutor.execute(query, any()) } returns executionResultMock
        every { executionResultMock.errors } returns emptyList()
        every { executionResultMock.getData<Publisher<ExecutionResult>>() } returns Flux.just(nestedExecutionResult)
        every { nestedExecutionResult.getData<String>() } returns "message 1"

        val responseEntity = DgsSSESubscriptionHandler(dgsQueryExecutor).subscriptionWithId(base64)
        assertThat(responseEntity.statusCode.is2xxSuccessful).isTrue
    }
}
