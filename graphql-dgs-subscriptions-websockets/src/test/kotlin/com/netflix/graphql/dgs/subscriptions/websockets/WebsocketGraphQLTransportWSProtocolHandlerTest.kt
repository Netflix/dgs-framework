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

package com.netflix.graphql.dgs.subscriptions.websockets

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.types.subscription.websockets.CloseCode
import com.netflix.graphql.types.subscription.websockets.Message
import com.netflix.graphql.types.subscription.websockets.MessageType
import graphql.ExecutionResult
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockkClass
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.reactivestreams.Publisher
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

@ExtendWith(MockKExtension::class)
class WebsocketGraphQLTransportWSProtocolHandlerTest {

    private lateinit var dgsWebsocketHandler: WebsocketGraphQLTransportWSProtocolHandler

    @BeforeEach
    fun setup() {
        dgsWebsocketHandler = WebsocketGraphQLTransportWSProtocolHandler(dgsQueryExecutor, Duration.ofMillis(1000))

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

    private fun queryMessage(session: WebSocketSession) = TextMessage(
        """{
                "type": "${MessageType.SUBSCRIBE}",
                "payload": {
                   "query": "{ hello }",
                   "variables": {},
                   "extensions": {}
                },
                "id": "${session.id}"
            }
        """.trimIndent()
    )

    private val connectionInitMessage = TextMessage(
        """{
                "type": "${MessageType.CONNECTION_INIT}",
                "payload": {
                   "auth": "test"
                }
            }
        """.trimIndent()
    )

    @Test
    fun query() {
        connect(session1)
        subscribe(session1, 3)
    }
    @Test
    fun testMultipleClients() {
        connect(session1)
        connect(session2)
        subscribe(session2, 3)
        subscribe(session1, 1)

        disconnect(session2)
        disconnect(session1)

        // ACK, NEXT, COMPLETE
        verify(exactly = 3) {
            session1.sendMessage(any())
        }

        // ACK, NEXT, NEXT, NEXT, COMPLETE
        verify(exactly = 5) {
            session2.sendMessage(any())
        }
    }

    @Test
    fun testConnectionInitTimeout() {
        every { session1.close(CloseStatus(CloseCode.ConnectionInitialisationTimeout.code, null)) } just Runs

        dgsWebsocketHandler.afterConnectionEstablished(session1)
        Thread.sleep(12000)
        verify { session1.close(CloseStatus(CloseCode.ConnectionInitialisationTimeout.code)) }
    }

    private fun handle(vararg textMessages: TextMessage) {
        dgsWebsocketHandler.afterConnectionEstablished(this.session1)
        for (message in textMessages) {
            dgsWebsocketHandler.handleTextMessage(this.session1, message)
        }
    }

    private fun connect(webSocketSession: WebSocketSession) {
        val currentNrOfSessions = dgsWebsocketHandler.sessions.size

        val slot = slot<TextMessage>()
        every { webSocketSession.sendMessage(capture(slot)) } just Runs

        val textMessage = TextMessage(
            """{
                "type": "${MessageType.CONNECTION_INIT}"
            }
            """.trimIndent()
        )

        dgsWebsocketHandler.afterConnectionEstablished(webSocketSession)
        dgsWebsocketHandler.handleTextMessage(webSocketSession, textMessage)

        Assertions.assertThat(dgsWebsocketHandler.sessions.size).isEqualTo(currentNrOfSessions + 1)

        val returnMessage = jacksonObjectMapper().readValue<Message>(slot.captured.asBytes())
        Assertions.assertThat(returnMessage.type).isEqualTo(MessageType.CONNECTION_ACK)
    }

    private fun disconnect(webSocketSession: WebSocketSession) {
        val currentNrOfSessions = dgsWebsocketHandler.sessions.size
        // every { webSocketSession.close(CloseStatus.NORMAL) } just Runs

        dgsWebsocketHandler.afterConnectionClosed(webSocketSession, CloseStatus.NORMAL)

        Assertions.assertThat(dgsWebsocketHandler.sessions.size).isEqualTo(currentNrOfSessions - 1)
    }

    private fun subscribe(webSocketSession: WebSocketSession, nrOfResults: Int) {

        every { webSocketSession.isOpen } returns true

        val results = (1..nrOfResults).map {
            val result1 = mockkClass(ExecutionResult::class)
            every { result1.getData<Any>() } returns it
            every { result1.extensions } returns mapOf<Any, Any>()
            every { result1.errors } returns listOf()
            every { result1.isDataPresent } returns true
            result1
        }

        every { executionResult.getData<Publisher<ExecutionResult>>() } returns
            Mono.just(results).flatMapMany { Flux.fromIterable(results) }

        every {
            dgsQueryExecutor.execute(
                "{ hello }",
                emptyMap(),
                emptyMap(),
                null,
                null,
                null
            )
        } returns executionResult

        dgsWebsocketHandler.handleTextMessage(webSocketSession, queryMessage(webSocketSession))
    }
}
