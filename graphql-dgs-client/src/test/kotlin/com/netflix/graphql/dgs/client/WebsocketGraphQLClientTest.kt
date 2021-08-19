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

package com.netflix.graphql.dgs.client

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
import graphql.GraphQLException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher
import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.stream.Collectors

class WebsocketGraphQLClientTest {
    companion object {
        private val VERIFY_TIMEOUT = Duration.ofSeconds(10)
        private val CONNECTION_ACK_MESSAGE = OperationMessage(GQL_CONNECTION_ACK, null, null)
        private val TEST_DATA_A = mapOf(Pair("a", 1), Pair("b", "hello"), Pair("c", false))
        private val TEST_DATA_B = mapOf(Pair("a", 2), Pair("b", null), Pair("c", true))
        private val TEST_DATA_C = mapOf(Pair("a", 3), Pair("b", "world"), Pair("c", false))
    }

    lateinit var subscriptionsClient: SubscriptionsTransportWsClient
    lateinit var client: WebsocketGraphQLClient
    lateinit var server: TestPublisher<OperationMessage>

    @BeforeEach
    fun setup() {
        subscriptionsClient = mockk(relaxed = true)
        client = WebsocketGraphQLClient(subscriptionsClient)
        server = TestPublisher.createCold()

        every { subscriptionsClient.receive() } returns server.flux()
    }

    @Test
    fun timesOutIfNoAckFromServer() {
        val client = WebsocketGraphQLClient(subscriptionsClient, Duration.ofSeconds(1))
        val responses = client.reactiveExecuteQuery("", emptyMap())
        StepVerifier.create(responses)
            .expectError(TimeoutException::class.java)
            .verify(VERIFY_TIMEOUT)
    }

    @Test
    fun errorsIfMessageOtherThanAckFromServer() {
        server.next(dataMessage(TEST_DATA_A, "1"))

        val responses = client.reactiveExecuteQuery("", emptyMap())
        StepVerifier.create(responses)
            .expectError(GraphQLException::class.java)
            .verify(VERIFY_TIMEOUT)
    }

    @Test
    fun sendsInitMessage() {
        server.next(CONNECTION_ACK_MESSAGE)
        server.next(OperationMessage(GQL_COMPLETE, null, "1"))

        client.reactiveExecuteQuery("", emptyMap()).blockLast()
        verify { subscriptionsClient.send(OperationMessage(GQL_CONNECTION_INIT, null, null)) }
    }

    @Test
    fun sendsQuery() {
        server.next(CONNECTION_ACK_MESSAGE)
        server.next(OperationMessage(GQL_COMPLETE, null, "1"))

        client.reactiveExecuteQuery("{ helloWorld }", emptyMap()).blockLast()
        verify { subscriptionsClient.send(OperationMessage(GQL_START, QueryPayload(emptyMap(), emptyMap(), null, "{ helloWorld }"), "1")) }
    }

    @Test
    fun parsesData() {
        server.next(CONNECTION_ACK_MESSAGE)
        server.next(dataMessage(TEST_DATA_A, "1"))

        val responses = client.reactiveExecuteQuery("", emptyMap())
        StepVerifier.create(responses.take(1))
            .expectSubscription()
            .expectNextMatches {
                it.extractValue<Int>("a") == 1
                        && it.extractValue<String>("b") == "hello"
                        && !it.extractValue<Boolean>("c")
            }
            .expectComplete()
            .verify(VERIFY_TIMEOUT)
    }

    @Test
    fun parsesMultipleData() {
        server.next(CONNECTION_ACK_MESSAGE)
        server.next(dataMessage(TEST_DATA_A, "1"))
        server.next(dataMessage(TEST_DATA_B, "1"))

        val responses = client.reactiveExecuteQuery("", emptyMap())
        StepVerifier.create(responses.take(2))
            .expectSubscription()
            .expectNextMatches {
                it.extractValue<Int>("a") == 1
                        && it.extractValue<String?>("b") == "hello"
                        && !it.extractValue<Boolean>("c")
            }
            .expectNextMatches {
                it.extractValue<Int>("a") == 2
                        && it.extractValue<String?>("b") == null
                        && it.extractValue("c")
            }
            .expectComplete()
            .verify(VERIFY_TIMEOUT)
    }

    @Test
    fun completesOnCompleteMessage() {
        server.next(CONNECTION_ACK_MESSAGE)
        server.next(dataMessage(TEST_DATA_A, "1"))
        server.next(OperationMessage(GQL_COMPLETE, null, "1"))

        val responses = client.reactiveExecuteQuery("", emptyMap())
        StepVerifier.create(responses)
            .expectSubscription()
            .expectNextMatches { it.extractValue<Int>("a") == 1 }
            .expectComplete()
            .verify(VERIFY_TIMEOUT)
    }

    @Test
    fun finishesOnGraphQLError() {
        server.next(CONNECTION_ACK_MESSAGE)
        server.next(OperationMessage(GQL_ERROR, "An error occurred", "1"))

        val responses = client.reactiveExecuteQuery("", emptyMap())
        StepVerifier.create(responses)
            .expectSubscription()
            .expectError(GraphQLException::class.java)
            .verify(VERIFY_TIMEOUT)
    }

    @Test
    fun sendsStopMessageIfCancelled() {
        server.next(CONNECTION_ACK_MESSAGE)

        val responses = client.reactiveExecuteQuery("", emptyMap())
        StepVerifier.create(responses)
            .expectSubscription()
            .thenAwait()
            .thenCancel()
            .verify(VERIFY_TIMEOUT)

        verifyOrder {
            subscriptionsClient.send(OperationMessage(GQL_CONNECTION_INIT, null, null))
            subscriptionsClient.send(OperationMessage(GQL_START, QueryPayload(emptyMap(), emptyMap(), null, ""), "1"))
            subscriptionsClient.send(OperationMessage(GQL_STOP, null, "1"))
        }
    }

    @Test
    fun handlesMultipleSubscriptions() {
        server.next(CONNECTION_ACK_MESSAGE)
        server.next(dataMessage(TEST_DATA_A, "1"))
        server.next(OperationMessage(GQL_COMPLETE, null, "1"))

        val responses1 = client.reactiveExecuteQuery("", emptyMap())
        val responses2 = client.reactiveExecuteQuery("", emptyMap())

        StepVerifier.create(responses1.map { it.extractValue<Int>("a") })
            .expectSubscription()
            .expectNext(1)
            .expectComplete()
            .verify(VERIFY_TIMEOUT)

        server.next(dataMessage(TEST_DATA_B, "2"))
        server.next(OperationMessage(GQL_COMPLETE, null, "2"))

        StepVerifier.create(responses2.map { it.extractValue<Int>("a") })
            .expectSubscription()
            .expectNext(2)
            .expectComplete()
            .verify(VERIFY_TIMEOUT)
    }

    @Test
    fun handlesConcurrentSubscriptions() {
        server.next(CONNECTION_ACK_MESSAGE)
        server.next(dataMessage(TEST_DATA_A, "1"))
        server.next(dataMessage(TEST_DATA_B, "2"))
        server.next(OperationMessage(GQL_COMPLETE, null, "2"))
        server.next(dataMessage(TEST_DATA_C, "1"))
        server.next(OperationMessage(GQL_COMPLETE, null, "1"))

        val responses1 = client.reactiveExecuteQuery("", emptyMap())
        val responses2 = client.reactiveExecuteQuery("", emptyMap())

        val responses = Flux.merge(
            responses1
                .map { it.extractValue<Int>("a") }
                .collect(Collectors.toList()),
            responses2
                .map { it.extractValue<Int>("a") }
                .collect(Collectors.toList()))
            .collect(Collectors.toList())
            .block()

        assertThat(responses).hasSameElementsAs(listOf(
            listOf(1, 3),
            listOf(2)
        ))
    }

    fun dataMessages(data: List<Map<String, Any?>>, id: String): Flux<OperationMessage> {
        return Flux
            .fromIterable(data)
            .map { OperationMessage(GQL_DATA, DataPayload(it, null), id) }
    }

    fun dataMessage(data: Map<String, Any?>, id: String): OperationMessage {
        return OperationMessage(GQL_DATA, DataPayload(data, null), id)
    }
}