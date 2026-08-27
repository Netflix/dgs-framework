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

package com.netflix.graphql.dgs.springgraphql

import com.netflix.graphql.dgs.internal.DefaultDgsGraphQLContextBuilder
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.Jackson3DgsJsonMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.dataloader.registries.ScheduledDataLoaderRegistry
import org.junit.jupiter.api.Test
import org.springframework.graphql.ExecutionGraphQlRequest
import org.springframework.graphql.ExecutionGraphQlResponse
import org.springframework.graphql.ExecutionGraphQlService
import reactor.core.publisher.Mono
import java.util.Optional
import java.util.function.Supplier

class SpringGraphQLDgsQueryExecutorTest {
    private val dataLoaderRegistry = mockk<ScheduledDataLoaderRegistry>(relaxed = true)

    private val dataLoaderProvider =
        mockk<DgsDataLoaderProvider> {
            every { buildRegistryWithContextSupplier(any<Supplier<Any>>()) } returns dataLoaderRegistry
        }

    /**
     * A ticker mode registry reschedules itself until it is closed, so failing to close it leaks a
     * task on the shared scheduled executor for every executed query.
     */
    @Test
    fun `closes the data loader registry once the query completes`() {
        val queryExecutor = queryExecutor { request -> Mono.just(responseFor(request)) }

        queryExecutor.execute("{ __typename }")

        verify(exactly = 1) { dataLoaderRegistry.close() }
    }

    @Test
    fun `closes the data loader registry when execution fails`() {
        val queryExecutor =
            queryExecutor { request ->
                request.toExecutionInput()
                Mono.error(RuntimeException("boom"))
            }

        assertThatThrownBy { queryExecutor.execute("{ __typename }") }.hasMessage("boom")

        verify(exactly = 1) { dataLoaderRegistry.close() }
    }

    private fun queryExecutor(executionService: ExecutionGraphQlService): SpringGraphQLDgsQueryExecutor =
        SpringGraphQLDgsQueryExecutor(
            executionService,
            DefaultDgsGraphQLContextBuilder(Optional.empty()),
            dataLoaderProvider,
            Jackson3DgsJsonMapper(),
            emptyList(),
        )

    private fun responseFor(request: ExecutionGraphQlRequest): ExecutionGraphQlResponse {
        // Building the execution input is what applies the configurer that creates the registry.
        request.toExecutionInput()
        return mockk(relaxed = true)
    }
}
