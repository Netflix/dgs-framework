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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.types.subscription.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
