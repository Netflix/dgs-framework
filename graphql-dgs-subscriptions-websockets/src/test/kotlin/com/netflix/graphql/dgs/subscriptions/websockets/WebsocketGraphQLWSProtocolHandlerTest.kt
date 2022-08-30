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

package com.netflix.graphql.dgs.subscriptions.websockets

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.types.subscription.DataPayload
import com.netflix.graphql.types.subscription.GQL_CONNECTION_ACK
import com.netflix.graphql.types.subscription.GQL_CONNECTION_INIT
import com.netflix.graphql.types.subscription.GQL_CONNECTION_TERMINATE
import com.netflix.graphql.types.subscription.GQL_DATA
import com.netflix.graphql.types.subscription.GQL_ERROR
import com.netflix.graphql.types.subscription.GQL_START
import com.netflix.graphql.types.subscription.GQL_STOP
import com.netflix.graphql.types.subscription.OperationMessage
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionResult
import graphql.execution.ResultPath
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockkClass
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.InstanceOfAssertFactories.MAP
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.reactivestreams.Publisher
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
class WebsocketGraphQLWSProtocolHandlerTest {

    private lateinit var dgsWebsocketHandler: WebsocketGraphQLWSProtocolHandler

    @BeforeEach
    fun setup() {
        dgsWebsocketHandler = WebsocketGraphQLWSProtocolHandler(dgsQueryExecutor)

        every { session1.id } returns "1"
        every { session2.id } returns "2"
    }

    @MockK
    lateinit var dgsQueryExecutor: DgsQueryExecutor

    @MockK
    lateinit var session1: WebSocketSession

    @MockK
    lateinit var session2: WebSocketSession

    @MockK
    lateinit var executionResult: ExecutionResult

    private val queryMessage = TextMessage(
        """{
                "type": "$GQL_START",
                "payload": {
                   "query": "{ hello }",
                   "variables": {},
                   "extensions": {}
                },
                "id": "123"
            }
        """.trimIndent()
    )

    private val queryMessageWithVariable = TextMessage(
        """{
                "type": "$GQL_START",
                "payload": {
                   "query": "query HELLO(${'$'}name: String){ hello(name:${'$'}name) }",
                   "variables": {"name": "Stranger"},
                   "extensions": {}
                },
                "id": "222"
            }
        """.trimIndent()
    )

    private val queryMessageWithNullVariable = TextMessage(
        """{
                "type": "$GQL_START",
                "payload": {
                   "query": "query HELLO(${'$'}name: String){ hello(name:${'$'}name) }",
                   "variables": null,
                   "extensions": {}
                },
                "id": "123"
            }
        """.trimIndent()
    )

    @Test
    fun testMultipleClients() {
        connect(session1)
        connect(session2)
        start(session2, 3)
        start(session1, 1)

        disconnect(session2)
        disconnect(session1)

        // ACK, DATA, COMPLETE
        verify(exactly = 3) {
            session1.sendMessage(any())
        }

        // ACK, DATA, DATA DATA, COMPLETE
        verify(exactly = 5) {
            session2.sendMessage(any())
        }
    }

    @Test
    fun testWithMultipleSubscriptionsPerSession() {
        connect(session1)
        start(session1, 1)
        startWithVariable(session1, 1)

        assertThat(dgsWebsocketHandler.sessions.size).isEqualTo(1)
        assertThat(dgsWebsocketHandler.subscriptions.size).isEqualTo(1)
        disconnect(session1)
        assertThat(dgsWebsocketHandler.sessions.size).isEqualTo(0)
    }

    @Test
    fun testWithQueryVariables() {
        connect(session1)
        startWithVariable(session1, 1)

        disconnect(session1)

        // ACK, DATA, COMPLETE
        verify(exactly = 3) {
            session1.sendMessage(any())
        }
    }

    @Test
    fun testWithNullQueryVariables() {
        connect(session1)
        startWithNullVariable(session1, 1)

        disconnect(session1)

        // ACK, DATA, COMPLETE
        verify(exactly = 3) {
            session1.sendMessage(any())
        }
    }

    @Test
    fun testWithError() {
        connect(session1)
        startWithError(session1)
        disconnect(session1)

        // ACK, ERROR
        verify(exactly = 2) {
            session1.sendMessage(any())
        }
    }

    @Test
    fun testWithErrorAfterData() {
        connect(session1)
        nextWithError(session1, 2)
        disconnect(session1)

        // ACK, ERROR
        verify(exactly = 4) {
            session1.sendMessage(any())
        }
    }

    @Test
    fun testWithStop() {
        connect(session1)
        start(session1, 1)
        stop(session1)
        disconnect(session1)

        // ACK, DATA, COMPLETE
        verify(exactly = 3) {
            session1.sendMessage(any())
        }
    }

    private fun connect(webSocketSession: WebSocketSession) {
        val currentNrOfSessions = dgsWebsocketHandler.sessions.size

        val slot = slot<TextMessage>()
        every { webSocketSession.sendMessage(capture(slot)) } just Runs

        val textMessage = TextMessage(
            """{
                "type": "$GQL_CONNECTION_INIT"
            }
            """.trimIndent()
        )

        dgsWebsocketHandler.handleTextMessage(webSocketSession, textMessage)

        assertThat(dgsWebsocketHandler.sessions.size).isEqualTo(currentNrOfSessions + 1)

        val returnMessage = jacksonObjectMapper().readValue<OperationMessage>(slot.captured.asBytes())
        assertThat(returnMessage.type).isEqualTo(GQL_CONNECTION_ACK)
    }

    private fun disconnect(webSocketSession: WebSocketSession) {
        val currentNrOfSessions = dgsWebsocketHandler.sessions.size
        every { webSocketSession.close() } just Runs

        val textMessage = TextMessage(
            """{
                "type": "$GQL_CONNECTION_TERMINATE"
            }
            """.trimIndent()
        )

        dgsWebsocketHandler.handleTextMessage(webSocketSession, textMessage)

        assertThat(dgsWebsocketHandler.sessions.size).isEqualTo(currentNrOfSessions - 1)

        verify { webSocketSession.close() }
    }

    private fun start(webSocketSession: WebSocketSession, nrOfResults: Int) {

        every { webSocketSession.isOpen } returns true

        val results = (1..nrOfResults).map {
            val result1 = mockkClass(ExecutionResult::class)
            every { result1.getData<Any>() } returns it
            every { result1.errors } returns emptyList()
            result1
        }

        every { executionResult.getData<Publisher<ExecutionResult>>() } returns
            Mono.just(results).flatMapMany { Flux.fromIterable(results) }

        every { dgsQueryExecutor.execute("{ hello }", emptyMap()) } returns executionResult

        dgsWebsocketHandler.handleTextMessage(webSocketSession, queryMessage)
    }

    private fun startWithVariable(webSocketSession: WebSocketSession, nrOfResults: Int) {

        every { webSocketSession.isOpen } returns true

        val results = (1..nrOfResults).map {
            val result1 = mockkClass(ExecutionResult::class)
            every { result1.getData<Any>() } returns it
            every { result1.errors } returns emptyList()
            result1
        }

        every { executionResult.getData<Publisher<ExecutionResult>>() } returns Mono.just(results).flatMapMany { Flux.fromIterable(results) }

        every { dgsQueryExecutor.execute("query HELLO(\$name: String){ hello(name:\$name) }", mapOf("name" to "Stranger")) } returns executionResult

        dgsWebsocketHandler.handleTextMessage(webSocketSession, queryMessageWithVariable)
    }

    private fun startWithNullVariable(webSocketSession: WebSocketSession, nrOfResults: Int) {

        every { webSocketSession.isOpen } returns true

        val results = (1..nrOfResults).map {
            val result1 = mockkClass(ExecutionResult::class)
            every { result1.getData<Any>() } returns it
            every { result1.errors } returns emptyList()
            result1
        }

        every { executionResult.getData<Publisher<ExecutionResult>>() } returns Mono.just(results).flatMapMany { Flux.fromIterable(results) }

        every { dgsQueryExecutor.execute("query HELLO(\$name: String){ hello(name:\$name) }", null) } returns executionResult

        dgsWebsocketHandler.handleTextMessage(webSocketSession, queryMessageWithNullVariable)
    }

    private fun startWithError(webSocketSession: WebSocketSession) {
        every { webSocketSession.isOpen } returns true
        every { executionResult.getData<Publisher<ExecutionResult>>() } returns Mono.error(RuntimeException("That's wrong!"))
        every { dgsQueryExecutor.execute("{ hello }", emptyMap()) } returns executionResult

        val slot = slot<TextMessage>()
        every { webSocketSession.sendMessage(capture(slot)) } just Runs

        dgsWebsocketHandler.handleTextMessage(webSocketSession, queryMessage)

        val returnMessage = jacksonObjectMapper().readValue<OperationMessage>(slot.captured.asBytes())
        assertThat(returnMessage.type).isEqualTo(GQL_ERROR)
        assertThat((returnMessage.payload as DataPayload).errors?.size).isEqualTo(1)
        assertThat((returnMessage.payload as DataPayload).errors?.get(0)).isEqualTo("That's wrong!")
    }

    private fun nextWithError(webSocketSession: WebSocketSession, nrOfResults: Int) {
        val results = (1..nrOfResults).map {
            val result1 = mockkClass(ExecutionResult::class)
            every { result1.getData<Any>() } returns null
            every { result1.errors } returns listOf(ExceptionWhileDataFetching(ResultPath.rootPath(), RuntimeException("Error in data fetcher"), null))
            result1
        }
        every { webSocketSession.isOpen } returns true
        every { executionResult.getData<Publisher<ExecutionResult>>() } returns Mono.just(results).flatMapMany { Flux.fromIterable(results) }
        every { dgsQueryExecutor.execute("{ hello }", emptyMap()) } returns executionResult

        val slotList = mutableListOf<TextMessage>()
        every { webSocketSession.sendMessage(capture(slotList)) } just Runs

        dgsWebsocketHandler.handleTextMessage(webSocketSession, queryMessage)

        val returnMessage = jacksonObjectMapper().readValue<OperationMessage>(slotList[0].asBytes())
        assertThat(returnMessage.type).isEqualTo(GQL_DATA)

        val payload = returnMessage.payload as DataPayload

        assertThat(payload.errors)
            .hasSize(1)
            .element(0)
            .asInstanceOf(MAP)
            .extracting("message")
            .asString()
            .isEqualTo("Exception while fetching data () : Error in data fetcher")
    }

    private fun stop(webSocketSession: WebSocketSession) {
        val currentNrOfSessions = dgsWebsocketHandler.sessions.size
        every { webSocketSession.close() } just Runs

        val textMessage = TextMessage(
            """{
            "type": "$GQL_STOP",
            "id": "123"
        }
            """.trimIndent()
        )

        dgsWebsocketHandler.handleTextMessage(webSocketSession, textMessage)

        assertThat(dgsWebsocketHandler.sessions.size).isEqualTo(currentNrOfSessions)
        assertThat(dgsWebsocketHandler.subscriptions[webSocketSession.id]?.get("123")).isNull()
    }
}
