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

package com.netflix.graphql.dgs

import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.method.FallbackEnvironmentArgumentResolver
import com.netflix.graphql.dgs.internal.method.InputArgumentResolver
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import graphql.ExecutionInput
import graphql.GraphQL
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.task.SimpleAsyncTaskExecutor
import java.util.Optional

/**
 * When an [org.springframework.core.task.AsyncTaskExecutor] is configured, data fetchers that do not
 * already return a CompletionStage or Publisher are automatically wrapped in a CompletableFuture, so
 * that sibling fields can be fetched concurrently. Exceptions thrown by such a data fetcher must
 * still reach the DataFetcherExceptionHandler unchanged, exactly as they do when no task executor is
 * configured.
 *
 * Setting `dgs.graphql.virtualthreads.enabled=true` is the supported way to configure such an
 * executor, but the behaviour under test depends only on an executor being present, not on the kind
 * of threads it creates. The executor below therefore uses ordinary platform threads, which also
 * keeps this test runnable on the Java 17 toolchain the project builds with.
 *
 * The data fetchers must not be declared to return Nothing. Nothing is a subtype of every type,
 * including CompletionStage, so CompletableFutureWrapper would decline to wrap them and the wrapping
 * path under test would never be exercised.
 */
class CompletableFutureWrappedDataFetcherTest {
    private val context = GenericApplicationContext()

    private val schemaProvider by lazy {
        DgsSchemaProvider(
            applicationContext = context,
            federationResolver = Optional.empty(),
            existingTypeDefinitionRegistry = Optional.empty(),
            methodDataFetcherFactory =
                MethodDataFetcherFactory(
                    argumentResolvers =
                        listOf(
                            InputArgumentResolver(DefaultInputObjectMapper()),
                            FallbackEnvironmentArgumentResolver(DefaultInputObjectMapper()),
                        ),
                    asyncTaskExecutor = SimpleAsyncTaskExecutor("dgs-test-async-task-executor-"),
                ),
        )
    }

    @Test
    fun `Throw the cause of InvocationTargetException from a wrapped data fetcher without arguments`() {
        @DgsComponent
        class Fetcher {
            @DgsQuery
            fun exception(): Int = throw CustomException(MESSAGE)
        }

        context.beanFactory.registerSingleton("exceptionFetcher", Fetcher())

        assertSingleError(
            schema = "type Query { exception: Int }",
            query = "{ result: exception }",
        )
    }

    @Test
    fun `Throw the cause of InvocationTargetException from a wrapped data fetcher with arguments`() {
        @DgsComponent
        class Fetcher {
            @DgsQuery
            fun exceptionWithMessage(
                @InputArgument message: String?,
            ): Int = throw CustomException(message)
        }

        context.beanFactory.registerSingleton("exceptionWithMessageFetcher", Fetcher())

        assertSingleError(
            schema = "type Query { exceptionWithMessage(message: String): Int }",
            query = """{ result: exceptionWithMessage(message: "$MESSAGE") }""",
        )
    }

    private fun assertSingleError(
        schema: String,
        query: String,
    ) {
        context.refresh()

        val graphQLSchema = schemaProvider.schema(schema).graphQLSchema
        val executionResult =
            GraphQL
                .newGraphQL(graphQLSchema)
                .build()
                .execute(
                    ExecutionInput
                        .newExecutionInput()
                        .graphQLContext(DgsContext(null, null))
                        .query(query)
                        .build(),
                )

        assertThat(executionResult.errors.size).isEqualTo(1)
        assertThat(executionResult.errors[0].path).isEqualTo(listOf("result"))
        assertThat(executionResult.errors[0].message).isEqualTo("Exception while fetching data (/result) : $MESSAGE")
    }

    // Deliberately unchecked: Spring's ReflectionUtils.invokeMethod, used by the no-argument
    // branch, converts checked exceptions into UndeclaredThrowableException, which would obscure
    // what this test is about.
    private class CustomException(
        message: String?,
    ) : RuntimeException(message)

    private companion object {
        const val MESSAGE = "Exception from wrapped data fetcher"
    }
}
