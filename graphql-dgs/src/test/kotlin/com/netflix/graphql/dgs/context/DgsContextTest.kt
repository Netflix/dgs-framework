/*
 * Copyright 2023 Netflix, Inc.
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

import com.netflix.graphql.dgs.*
import com.netflix.graphql.dgs.internal.DefaultDgsGraphQLContextBuilder
import com.netflix.graphql.dgs.internal.DefaultDgsQueryExecutor
import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.method.DataFetchingEnvironmentArgumentResolver
import com.netflix.graphql.dgs.internal.method.InputArgumentResolver
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import org.assertj.core.api.Assertions.assertThat
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.BatchLoaderWithContext
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpHeaders
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

internal class DgsContextTest {

    private val applicationContextRunner: ApplicationContextRunner = ApplicationContextRunner()
        .withBean(DgsDataLoaderProvider::class.java)

    @Test
    fun `getRequestData should return request data with headers`() {
        applicationContextRunner.withBean(DgsTestComponent::class.java).run { context ->
            val dgsQueryExecutor = createQueryExecutor(context, "type Query { hello(name: String): String }")

            val httpHeaders = HttpHeaders()
            httpHeaders.add("x-name-prefix", "Hello ")

            val hello = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                """{hello(name: "World")}""",
                "data.hello",
                emptyMap(),
                String::class.java,
                httpHeaders
            )

            assertThat(hello).isEqualTo("Hello World")
        }
    }

    private fun createQueryExecutor(context: ApplicationContext, schemaString: String): DefaultDgsQueryExecutor {
        val provider = DgsSchemaProvider(
            applicationContext = context,
            federationResolver = Optional.empty(),
            existingTypeDefinitionRegistry = Optional.empty(),
            methodDataFetcherFactory = MethodDataFetcherFactory(
                listOf(
                    InputArgumentResolver(DefaultInputObjectMapper()),
                    DataFetchingEnvironmentArgumentResolver()
                )
            )
        )

        return DefaultDgsQueryExecutor(
            defaultSchema = provider.schema(schemaString).graphQLSchema,
            schemaProvider = provider,
            dataLoaderProvider = context.getBean(DgsDataLoaderProvider::class.java),
            contextBuilder = DefaultDgsGraphQLContextBuilder(Optional.empty()),
            instrumentation = SimplePerformantInstrumentation.INSTANCE,
            queryExecutionStrategy = AsyncExecutionStrategy(),
            mutationExecutionStrategy = AsyncSerialExecutionStrategy(),
            idProvider = Optional.empty()
        )
    }

    @DgsComponent
    class DgsTestComponent {
        @DgsQuery
        fun hello(@InputArgument name: String, dfe: DgsDataFetchingEnvironment): CompletableFuture<String> {
            val loader = dfe.getDataLoader<String, String>(HelloLoader::class.java)
            return loader.load(name)
        }

        @DgsDataLoader
        class HelloLoader : BatchLoaderWithContext<String, String> {
            override fun load(keys: List<String>, env: BatchLoaderEnvironment): CompletionStage<List<String>> {
                val prefix = DgsContext.getRequestData(env)?.headers?.getFirst("x-name-prefix") ?: ""
                return CompletableFuture.completedFuture(keys.map { prefix + it })
            }
        }
    }
}
