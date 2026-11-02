/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.graphql.dgs.context

import graphql.ExecutionInput
import graphql.GraphQLContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DgsContextTest {

    private fun buildGraphQLContextWithDgsContext(dgsContext: DgsContext): GraphQLContext {
        // Build ExecutionInput with DgsContext as consumer to properly populate the GraphQLContext
        val executionInput = ExecutionInput.newExecutionInput()
            .query("{ __typename }")
            .graphQLContext(dgsContext)
            .build()
        return executionInput.graphQLContext
    }

    @Test
    fun `fromOrNull should return null when DgsContext is not present`() {
        val graphQLContext = GraphQLContext.newContext().build()

        val result = DgsContext.fromOrNull(graphQLContext)

        assertThat(result).isNull()
    }

    @Test
    fun `fromOrNull should return DgsContext when present`() {
        val dgsContext = DgsContext(customContext = "testContext", requestData = null)
        val graphQLContext = buildGraphQLContextWithDgsContext(dgsContext)

        val result = DgsContext.fromOrNull(graphQLContext)

        assertThat(result).isNotNull
        assertThat(result?.customContext).isEqualTo("testContext")
    }

    @Test
    fun `from should throw NullPointerException when DgsContext is not present`() {
        val graphQLContext = GraphQLContext.newContext().build()

        assertThrows<NullPointerException> {
            DgsContext.from(graphQLContext)
        }
    }

    @Test
    fun `from should return DgsContext when present`() {
        val dgsContext = DgsContext(customContext = "testContext", requestData = null)
        val graphQLContext = buildGraphQLContextWithDgsContext(dgsContext)

        val result = DgsContext.from(graphQLContext)

        assertThat(result).isNotNull
        assertThat(result.customContext).isEqualTo("testContext")
    }
}
