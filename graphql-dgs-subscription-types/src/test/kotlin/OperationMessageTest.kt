import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.types.subscription.*
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

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

class OperationMessageTest {
    companion object {
        val MAPPER = jacksonObjectMapper()

        @JvmStatic
        fun validMessages() = listOf(
            Arguments.of(
                """{"type": "connection_init"}""",
                OperationMessage(GQL_CONNECTION_INIT, null, "")
            ),
            Arguments.of(
                """
                {"type": "connection_init",
                 "payload": {}}
                """.trimIndent(),
                OperationMessage(GQL_CONNECTION_INIT, JSONObject(), "")
            ),
            Arguments.of(
                """
                {"type": "stop", 
                 "id": "3"}
                """.trimIndent(),
                OperationMessage(GQL_STOP, null, "3")
            ),
            Arguments.of(
                """
                {"type": "stop", 
                 "id": 3}
                """.trimIndent(),
                OperationMessage(GQL_STOP, null, "3")
            ),
            Arguments.of(
                """
                {"type": "start",
                 "payload": {
                    "query": "my-query"
                 },
                 "id": "3"}
                """.trimIndent(),
                OperationMessage(GQL_START, QueryPayload(null, emptyMap(), null, "my-query"), "3")
            ),
            Arguments.of(
                """
                {"type": "start",
                 "payload": {
                    "operationName": "query",
                    "query": "my-query"
                 },
                 "id": "3"}
                """.trimIndent(),
                OperationMessage(GQL_START, QueryPayload(null, emptyMap(), "query", "my-query"), "3")
            ),
            Arguments.of(
                """
                {"type": "start",
                 "payload": {
                    "operationName": "query",
                    "extensions": {"a": "b"},
                    "query": "my-query"
                 },
                 "id": "3"}
                """.trimIndent(),
                OperationMessage(GQL_START, QueryPayload(null, mapOf(Pair("a", "b")), "query", "my-query"), "3")
            ),
            Arguments.of(
                """
                {"type": "start",
                 "payload": {
                    "operationName": "query",
                    "extensions": {"a": "b"},
                    "variables": {"c": "d"},
                    "query": "my-query"
                 },
                 "id": "3"}
                """.trimIndent(),
                OperationMessage(GQL_START, QueryPayload(mapOf(Pair("c", "d")), mapOf(Pair("a", "b")), "query", "my-query"), "3")
            ),
            Arguments.of(
                """
                {"type": "data",
                 "payload": {
                    "data": {
                        "a": 1,
                        "b": "hello",
                        "c": false
                    }
                 },
                 "id": "3"}
                """.trimIndent(),
                OperationMessage(GQL_DATA, DataPayload(mapOf(Pair("a", 1), Pair("b", "hello"), Pair("c", false))), "3")
            ),
            Arguments.of(
                """
                {"type": "data",
                 "payload": {
                    "errors": ["an-error"]
                 },
                 "id": "3"}
                """.trimIndent(),
                OperationMessage(GQL_DATA, DataPayload(null, listOf("an-error")), "3")
            ),
            Arguments.of(
                """
                {"type": "data",
                 "payload": {
                    "data": {
                        "a": 1,
                        "b": "hello",
                        "c": false
                    },
                    "errors": ["an-error"]
                 },
                 "id": "3"}
                """.trimIndent(),
                OperationMessage(GQL_DATA, DataPayload(mapOf(Pair("a", 1), Pair("b", "hello"), Pair("c", false)), listOf("an-error")), "3")
            ),
        )
    }

    @ParameterizedTest
    @MethodSource("validMessages")
    fun deserializes(message: String, expected: OperationMessage) {
        val deserialized = deserialize(message)
        Assertions.assertEquals(expected, deserialized)
    }

    @Test
    fun rejectsMessageWithoutType() {
        assertFailsToDeserialize<MissingKotlinParameterException>("""{"id": "2"}""")
    }

    @Test
    fun rejectsQueryMessageWithoutQuery() {
        assertFailsToDeserialize<JsonMappingException>(
            """
                {"type": "connection_init",
                 "payload": {
                    "operationName": "query",
                    "extensions": {"a": "b"},
                    "variables": {"c": "d"},
                 }
                 "id": "2"}
            """.trimIndent()
        )
    }

    private inline fun <reified E : Throwable> assertFailsToDeserialize(message: String) {
        assertThrows<E> { deserialize(message) }
    }

    private fun deserialize(message: String) =
        MAPPER.readValue(message, object : TypeReference<OperationMessage>() {})
}
