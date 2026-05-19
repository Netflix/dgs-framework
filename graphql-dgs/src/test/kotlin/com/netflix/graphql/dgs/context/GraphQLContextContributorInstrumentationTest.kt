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

import com.netflix.graphql.dgs.internal.DgsRequestData
import graphql.ExecutionInput
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters
import graphql.schema.GraphQLSchema
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GraphQLContextContributorInstrumentationTest {

    @Test
    fun `createState should not throw NPE when DgsContext is not present`() {
        val contributor = mockk<GraphQLContextContributor>(relaxed = true)
        val instrumentation = GraphQLContextContributorInstrumentation(listOf(contributor))

        val executionInput = ExecutionInput.newExecutionInput()
            .query("{ __typename }")
            .build()
        val schema = mockk<GraphQLSchema>()
        val parameters = InstrumentationCreateStateParameters(schema, executionInput)

        val result = instrumentation.createState(parameters)

        assertThat(result).isNull()
        verify { contributor.contribute(any(), any(), null) }
    }

    @Test
    fun `createState should pass requestData when DgsContext is present`() {
        val contributor = mockk<GraphQLContextContributor>(relaxed = true)
        val instrumentation = GraphQLContextContributorInstrumentation(listOf(contributor))

        val requestData = mockk<DgsRequestData>()
        val dgsContext = DgsContext(customContext = null, requestData = requestData)
        val executionInput = ExecutionInput.newExecutionInput()
            .query("{ __typename }")
            .graphQLContext(dgsContext)
            .build()
        val schema = mockk<GraphQLSchema>()
        val parameters = InstrumentationCreateStateParameters(schema, executionInput)

        val result = instrumentation.createState(parameters)

        assertThat(result).isNull()
        verify { contributor.contribute(any(), any(), requestData) }
    }

    @Test
    fun `createState should skip contributors when list is empty`() {
        val instrumentation = GraphQLContextContributorInstrumentation(emptyList())

        val executionInput = ExecutionInput.newExecutionInput()
            .query("{ __typename }")
            .build()
        val schema = mockk<GraphQLSchema>()
        val parameters = InstrumentationCreateStateParameters(schema, executionInput)

        val result = instrumentation.createState(parameters)

        assertThat(result).isNull()
    }

    @Test
    fun `createState should handle null graphQLContext`() {
        val contributor = mockk<GraphQLContextContributor>(relaxed = true)
        val instrumentation = GraphQLContextContributorInstrumentation(listOf(contributor))

        val executionInput = mockk<ExecutionInput>()
        every { executionInput.graphQLContext } returns null
        val schema = mockk<GraphQLSchema>()
        val parameters = InstrumentationCreateStateParameters(schema, executionInput)

        val result = instrumentation.createState(parameters)

        assertThat(result).isNull()
        verify(exactly = 0) { contributor.contribute(any(), any(), any()) }
    }
}
