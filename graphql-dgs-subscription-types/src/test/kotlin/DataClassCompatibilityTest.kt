/*
 * Copyright 2026 Netflix, Inc.
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

import com.netflix.graphql.types.subscription.DataPayload
import com.netflix.graphql.types.subscription.Error
import com.netflix.graphql.types.subscription.OperationMessage
import com.netflix.graphql.types.subscription.QueryPayload
import com.netflix.graphql.types.subscription.SSEDataPayload
import com.netflix.graphql.types.subscription.websockets.ExecutionResult
import com.netflix.graphql.types.subscription.websockets.Message
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DataClassCompatibilityTest {
    @Test
    fun `converted subscription data classes keep component copy and hash behavior`() {
        val operationMessage = OperationMessage("start", null, null)
        assertThat(listOf(operationMessage.component1(), operationMessage.component2(), operationMessage.component3()))
            .containsExactly("start", null, null)
        assertThat(operationMessage.copy("start", null, null)).isEqualTo(operationMessage)
        assertKotlinHash(operationMessage, "start", null, null)
        assertThat(operationMessage.toString()).isEqualTo("OperationMessage(type=start, payload=null, id=null)")

        val dataPayload = DataPayload("data", null)
        assertThat(listOf(dataPayload.component1(), dataPayload.component2())).containsExactly("data", null)
        assertThat(dataPayload.copy("data", null)).isEqualTo(dataPayload)
        assertKotlinHash(dataPayload, "data", null)
        assertThat(dataPayload.toString()).isEqualTo("DataPayload(data=data, errors=null)")

        val ssePayload = SSEDataPayload(null, listOf("error"), "subscription", "event")
        assertThat(listOf(ssePayload.component1(), ssePayload.component2(), ssePayload.component3(), ssePayload.component4()))
            .containsExactly(null, listOf("error"), "subscription", "event")
        assertThat(ssePayload.copy(null, listOf("error"), "subscription", "event")).isEqualTo(ssePayload)
        assertKotlinHash(ssePayload, null, listOf("error"), "subscription", "event")

        val queryPayload = QueryPayload(null, null, "operation", "query", "key")
        assertThat(
            listOf(
                queryPayload.component1(), queryPayload.component2(), queryPayload.component3(),
                queryPayload.component4(), queryPayload.component5(),
            ),
        ).containsExactly(null, null, "operation", "query", "key")
        assertThat(queryPayload.copy(null, null, "operation", "query", "key")).isEqualTo(queryPayload)
        assertKotlinHash(queryPayload, null, null, "operation", "query", "key")

        val error = Error("message")
        assertThat(error.component1()).isEqualTo("message")
        assertThat(error.copy("message")).isEqualTo(error)
        assertKotlinHash(error, "message")

        val executionResult = ExecutionResult("data", emptyList())
        assertThat(listOf(executionResult.component1(), executionResult.component2())).containsExactly("data", emptyList<Any>())
        assertThat(executionResult.copy("data", emptyList())).isEqualTo(executionResult)
        assertKotlinHash(executionResult, "data", emptyList<Any>())

        val map = mapOf("key" to "value")
        val init = Message.ConnectionInitMessage(map)
        assertThat(init.component1()).isEqualTo(map)
        assertThat(init.copy(map)).isEqualTo(init)
        assertKotlinHash(init, map)
        assertThat(init.toString()).isEqualTo("ConnectionInitMessage(payload={key=value})")

        val ack = Message.ConnectionAckMessage(map)
        assertThat(ack.component1()).isEqualTo(map)
        assertThat(ack.copy(map)).isEqualTo(ack)
        assertKotlinHash(ack, map)

        val ping = Message.PingMessage(map)
        assertThat(ping.component1()).isEqualTo(map)
        assertThat(ping.copy(map)).isEqualTo(ping)
        assertKotlinHash(ping, map)

        val pong = Message.PongMessage(map)
        assertThat(pong.component1()).isEqualTo(map)
        assertThat(pong.copy(map)).isEqualTo(pong)
        assertKotlinHash(pong, map)

        val payload = Message.SubscribeMessage.Payload("operation", "query", map, map)
        assertThat(
            listOf(payload.component1(), payload.component2(), payload.component3(), payload.component4()),
        ).containsExactly("operation", "query", map, map)
        assertThat(payload.copy("operation", "query", map, map)).isEqualTo(payload)
        assertKotlinHash(payload, "operation", "query", map, map)

        val subscribe = Message.SubscribeMessage("id", payload)
        assertThat(listOf(subscribe.component1(), subscribe.component2())).containsExactly("id", payload)
        assertThat(subscribe.copy("id", payload)).isEqualTo(subscribe)
        assertKotlinHash(subscribe, "id", payload)

        val next = Message.NextMessage("id", executionResult)
        assertThat(listOf(next.component1(), next.component2())).containsExactly("id", executionResult)
        assertThat(next.copy("id", executionResult)).isEqualTo(next)
        assertKotlinHash(next, "id", executionResult)

        val errorMessage = Message.ErrorMessage("id", listOf("error"))
        assertThat(listOf(errorMessage.component1(), errorMessage.component2())).containsExactly("id", listOf("error"))
        assertThat(errorMessage.copy("id", listOf("error"))).isEqualTo(errorMessage)
        assertKotlinHash(errorMessage, "id", listOf("error"))

        val complete = Message.CompleteMessage("id")
        assertThat(complete.component1()).isEqualTo("id")
        assertThat(complete.copy("id")).isEqualTo(complete)
        assertKotlinHash(complete, "id")
    }

    private fun assertKotlinHash(instance: Any, vararg properties: Any?) {
        val expected = properties.fold(0) { hash, property -> 31 * hash + (property?.hashCode() ?: 0) }
        assertThat(instance.hashCode()).isEqualTo(expected)
    }
}
