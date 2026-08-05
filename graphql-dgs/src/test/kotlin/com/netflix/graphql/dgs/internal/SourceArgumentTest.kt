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

package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.Source
import com.netflix.graphql.dgs.internal.method.DataFetchingEnvironmentArgumentResolver
import com.netflix.graphql.dgs.internal.method.FallbackEnvironmentArgumentResolver
import com.netflix.graphql.dgs.internal.method.InputArgumentResolver
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import com.netflix.graphql.dgs.internal.method.SourceArgumentResolver
import graphql.GraphQL
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import java.util.Optional

internal class SourceArgumentTest {
    private val contextRunner = ApplicationContextRunner()

    private fun schemaProvider(applicationContext: ApplicationContext) =
        DgsSchemaProvider(
            applicationContext = applicationContext,
            federationResolver = Optional.empty(),
            existingTypeDefinitionRegistry = Optional.empty(),
            schemaLocations = listOf("source-argument-test/schema.graphqls"),
            methodDataFetcherFactory =
                MethodDataFetcherFactory(
                    listOf(
                        InputArgumentResolver(DefaultInputObjectMapper()),
                        DataFetchingEnvironmentArgumentResolver(applicationContext),
                        SourceArgumentResolver(),
                        FallbackEnvironmentArgumentResolver(DefaultInputObjectMapper()),
                    ),
                ),
        )

    data class Show(
        val title: String,
    ) : BaseShow()

    open class BaseShow

    @Test
    fun `@Source argument`() {
        @DgsComponent
        class Fetcher {
            @DgsQuery
            fun shows(): List<Show> = listOf(Show("Stranger Things"))

            @DgsData(parentType = "Show")
            fun description(
                @Source show: Show,
            ): String = "Description of ${show.title}"
        }

        contextRunner.withBean(Fetcher::class.java).run { context ->
            val provider = schemaProvider(context)
            val schema = provider.schema().graphQLSchema

            val build = GraphQL.newGraphQL(schema).build()
            val executionResult =
                build.execute(
                    """{
                |   shows {
                |       title
                |       description
                |   }
                |}
                    """.trimMargin(),
                )

            assertThat(executionResult.errors).isEmpty()
            assertThat(executionResult.isDataPresent).isTrue
            val data = executionResult.getData<Map<String, *>>()

            @Suppress("UNCHECKED_CAST")
            val showData = (data["shows"] as List<Map<*, *>>)[0]
            assertThat(showData["title"]).isEqualTo("Stranger Things")
            assertThat(showData["description"]).isEqualTo("Description of Stranger Things")
        }
    }

    @Test
    fun `@Source argument could be a base type`() {
        @DgsComponent
        class Fetcher {
            @DgsQuery
            fun shows(): List<Show> = listOf(Show("Stranger Things"))

            @DgsData(parentType = "Show")
            fun description(
                @Source show: BaseShow,
            ): String = "Description of ${(show as Show).title}"
        }

        contextRunner.withBean(Fetcher::class.java).run { context ->
            val provider = schemaProvider(context)
            val schema = provider.schema().graphQLSchema

            val build = GraphQL.newGraphQL(schema).build()
            val executionResult =
                build.execute(
                    """{
                |   shows {
                |       title
                |       description
                |   }
                |}
                    """.trimMargin(),
                )

            assertThat(executionResult.errors).isEmpty()
            assertThat(executionResult.isDataPresent).isTrue
            val data = executionResult.getData<Map<String, *>>()

            @Suppress("UNCHECKED_CAST")
            val showData = (data["shows"] as List<Map<*, *>>)[0]
            assertThat(showData["title"]).isEqualTo("Stranger Things")
            assertThat(showData["description"]).isEqualTo("Description of Stranger Things")
        }
    }

    @Test
    fun `Incorrect @Source argument type`() {
        @DgsComponent
        class Fetcher {
            @DgsQuery
            fun shows(): List<Show> = listOf(Show("Stranger Things"))

            @DgsData(parentType = "Show")
            fun description(
                @Source show: String,
            ): String = "Should not be called"
        }

        contextRunner.withBean(Fetcher::class.java).run { context ->
            val provider = schemaProvider(context)
            val schema = provider.schema().graphQLSchema

            val build = GraphQL.newGraphQL(schema).build()
            val executionResult =
                build.execute(
                    """{
                |   shows {
                |       title
                |       description
                |   }
                |}
                    """.trimMargin(),
                )

            assertThat(executionResult.errors).isNotEmpty()
            assertThat(
                executionResult.errors[0].message,
            ).contains("Invalid source type 'com.netflix.graphql.dgs.internal.SourceArgumentTest\$Show'. Expected type 'java.lang.String'")
        }
    }

    @Test
    fun `Using @Source on a root datafetcher should fail`() {
        @DgsComponent
        class Fetcher {
            @DgsQuery
            fun shows(
                @Source something: String,
            ): List<Show> = listOf(Show("Stranger Things"))
        }

        contextRunner.withBean(Fetcher::class.java).run { context ->
            val provider = schemaProvider(context)
            val schema = provider.schema().graphQLSchema

            val build = GraphQL.newGraphQL(schema).build()
            val executionResult =
                build.execute(
                    """{
                |   shows {
                |       title
                |   }
                |}
                    """.trimMargin(),
                )

            assertThat(executionResult.errors).isNotEmpty()
            assertThat(
                executionResult.errors[0].message,
            ).contains("Source is null. Are you trying to use @Source on a root field (e.g. @DgsQuery)?")
        }
    }
}
