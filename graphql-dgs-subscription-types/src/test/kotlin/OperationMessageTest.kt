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

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.netflix.graphql.types.subscription.*
import org.assertj.core.api.Assertions.assertThat
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

    @Test
    fun rejectsMessageWithoutType() {
        assertFailsToDeserialize<MissingKotlinParameterException>("""{"id": "2"}""")
    }

    @ParameterizedTest
    @MethodSource("validMessages")
    fun deserializes(message: String, expected: OperationMessage) {
        val deserialized = deserialize(message)
        assertThat(expected).isEqualTo(deserialized)
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
        MAPPER.readValue(message, jacksonTypeRef<OperationMessage>())

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
                OperationMessage(GQL_CONNECTION_INIT, EmptyPayload)
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
                OperationMessage(GQL_START, QueryPayload(query = "my-query"), "3")
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
                OperationMessage(GQL_START, QueryPayload(operationName = "query", query = "my-query"), "3")
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
                OperationMessage(
                    GQL_START,
                    QueryPayload(
                        extensions = mapOf(Pair("a", "b")),
                        operationName = "query",
                        query = "my-query"
                    ),
                    "3"
                )
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
                OperationMessage(
                    GQL_START,
                    QueryPayload(
                        variables = mapOf(Pair("c", "d")),
                        extensions = mapOf(Pair("a", "b")),
                        operationName = "query",
                        query = "my-query"
                    ),
                    "3"
                )
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
                OperationMessage(
                    GQL_DATA,
                    DataPayload(data = mapOf(Pair("a", 1), Pair("b", "hello"), Pair("c", false))),
                    "3"
                )
            ),
            Arguments.of(
                """
                {"type": "data",
                 "payload": {
                    "errors": ["an-error"]
                 },
                 "id": "3"}
                """.trimIndent(),
                OperationMessage(GQL_DATA, DataPayload(data = null, listOf("an-error")), "3")
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
}
