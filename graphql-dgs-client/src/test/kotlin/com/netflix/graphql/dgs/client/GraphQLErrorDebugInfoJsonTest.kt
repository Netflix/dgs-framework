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

package com.netflix.graphql.dgs.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GraphQLErrorDebugInfoJsonTest {
    private val mappers = listOf(
        Jackson2DgsJsonMapperAdapter.defaultMapper(),
        Jackson3DgsJsonMapperAdapter.defaultMapper(),
    )

    @Test
    fun `omitted properties retain data class defaults and capture additional fields`() {
        mappers.forEach { mapper ->
            val debugInfo = mapper.readValue(
                """{"requestId":"abc"}""",
                GraphQLErrorDebugInfo::class.java,
            )

            assertThat(debugInfo.subquery).isEmpty()
            assertThat(debugInfo.variables).isEmpty()
            assertThat(debugInfo.additionalInformation).containsExactlyEntriesOf(mapOf("requestId" to "abc"))
        }
    }

    @Test
    fun `explicit null uses the Kotlin defaults for non-null defaulted properties`() {
        mappers.forEach { mapper ->
            val debugInfo = mapper.readValue(
                """{"subquery":null,"variables":null}""",
                GraphQLErrorDebugInfo::class.java,
            )

            assertThat(debugInfo.subquery).isEmpty()
            assertThat(debugInfo.variables).isEmpty()
        }
    }
}
