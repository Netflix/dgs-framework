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
import com.netflix.graphql.dgs.subscriptions.sse.DgsSSEAutoConfig
import com.netflix.graphql.dgs.subscriptions.websockets.DgsWebSocketAutoConfig
import graphql.GraphQLException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.test.StepVerifier

@SpringBootTest(
    classes = [DgsWebSocketAutoConfig::class, TestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@EnableAutoConfiguration(exclude = [DgsSSEAutoConfig::class])
internal class WebSocketGraphQLClientWithDGSServerTest {
    private val logger = LoggerFactory.getLogger(WebSocketGraphQLClientWithDGSServerTest::class.java)

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @LocalServerPort
    lateinit var port: Integer

    lateinit var client: WebSocketGraphQLClient

    @BeforeEach
    fun setup() {
        client = WebSocketGraphQLClient("ws://localhost:$port/subscriptions", ReactorNettyWebSocketClient())
    }

    @Test
    fun `A successful subscription should publish ticks`() {
        val reactiveExecuteQuery =
            client.reactiveExecuteQuery("subscription s {numbers}", emptyMap()).mapNotNull { r -> r.data["numbers"] }

        StepVerifier.create(reactiveExecuteQuery)
            .expectNext(1, 2, 3)
            .expectComplete()
            .verify()
    }

    @Test
    fun `A connection error should result in a WebClientException`() {

        Assertions.assertThrows(GraphQLException::class.java) {
            client.reactiveExecuteQuery("subscription {withError}", emptyMap()).blockLast()
        }
    }

    @Test
    fun `A badly formatted query should result in a GraphQLException`() {

        Assertions.assertThrows(GraphQLException::class.java) {
            client.reactiveExecuteQuery("invalid query", emptyMap()).blockLast()
        }
    }

    @Test
    fun `An invalid query should result in a UnknownOperationException`() {

        Assertions.assertThrows(GraphQLException::class.java) {
            client.reactiveExecuteQuery("subscriptions { unkownField }", emptyMap()).blockLast()
        }
    }
}
