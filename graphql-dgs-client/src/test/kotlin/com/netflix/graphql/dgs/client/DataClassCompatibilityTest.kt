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

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DataClassCompatibilityTest {
    @Test
    fun `converted client data classes keep component copy and hash behavior`() {
        val debugInfo = GraphQLErrorDebugInfo("subquery", mapOf("id" to 1), mapOf("trace" to "abc"))
        val extensions = GraphQLErrorExtensions(ErrorType.BAD_REQUEST, "invalid", "gateway", debugInfo, "bad-input")
        val error = GraphQLError("failure", listOf("user", 1), listOf(mapOf("line" to 2)), extensions)

        assertThat(listOf(error.component1(), error.component2(), error.component3(), error.component4()))
            .containsExactly("failure", listOf("user", 1), listOf(mapOf("line" to 2)), extensions)
        assertThat(error.copy("failure", listOf("user", 1), listOf(mapOf("line" to 2)), extensions)).isEqualTo(error)
        assertKotlinHash(error, "failure", listOf("user", 1), listOf(mapOf("line" to 2)), extensions)
        assertThat(error.toString())
            .isEqualTo("GraphQLError(message=failure, path=[user, 1], locations=[{line=2}], extensions=$extensions)")

        assertThat(
            listOf(
                extensions.component1(), extensions.component2(), extensions.component3(),
                extensions.component4(), extensions.component5(),
            ),
        ).containsExactly(ErrorType.BAD_REQUEST, "invalid", "gateway", debugInfo, "bad-input")
        assertThat(extensions.copy(ErrorType.BAD_REQUEST, "invalid", "gateway", debugInfo, "bad-input"))
            .isEqualTo(extensions)
        assertKotlinHash(extensions, ErrorType.BAD_REQUEST, "invalid", "gateway", debugInfo, "bad-input")

        assertThat(listOf(debugInfo.component1(), debugInfo.component2(), debugInfo.component3()))
            .containsExactly("subquery", mapOf("id" to 1), mapOf("trace" to "abc"))
        assertThat(debugInfo.copy("subquery", mapOf("id" to 1), mapOf("trace" to "abc"))).isEqualTo(debugInfo)
        assertKotlinHash(debugInfo, "subquery", mapOf("id" to 1), mapOf("trace" to "abc"))

        val headers = mapOf("x-test" to listOf("one"))
        val httpResponse = HttpResponse(418, "body", headers)
        assertThat(listOf(httpResponse.component1(), httpResponse.component2(), httpResponse.component3()))
            .containsExactly(418, "body", headers)
        assertThat(httpResponse.copy(418, "body", headers)).isEqualTo(httpResponse)
        assertKotlinHash(httpResponse, 418, "body", headers)

        val details = RequestDetails("request", null)
        assertThat(listOf(details.component1(), details.component2())).containsExactly("request", null)
        assertThat(details.copy("request", null)).isEqualTo(details)
        assertKotlinHash(details, "request", null)

        val mapper = ObjectMapper()
        val response = GraphQLResponse("{}", headers, mapper)
        assertThat(listOf(response.component1(), response.component2())).containsExactly("{}", headers)
        assertThat(response.copy("{}", headers, mapper)).isEqualTo(response)
        assertKotlinHash(response, "{}", headers, mapper)
        assertThat(response.toString()).startsWith("GraphQLResponse(json={}, headers=$headers, mapper=")
    }

    private fun assertKotlinHash(instance: Any, vararg properties: Any?) {
        val expected = properties.fold(0) { hash, property -> 31 * hash + (property?.hashCode() ?: 0) }
        assertThat(instance.hashCode()).isEqualTo(expected)
    }
}
