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

import com.netflix.graphql.dgs.DefaultDgsFederationResolverTest.Movie
import com.netflix.graphql.dgs.exceptions.DuplicateEntityFetcherException
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class DuplicateEntityFetcherTest {
    @Test
    fun `Startup should fail if a duplicate EntityFetcher exists`() {
        val contextRunner =
            ApplicationContextRunner()
                .withBean(DgsSchemaProvider::class.java)
                .withBean(EntityFetcherConfig::class.java)
                .withBean(MethodDataFetcherFactory::class.java)

        contextRunner.run { context ->
            context.start()

            val exception =
                catchThrowable {
                    context.getBean(DgsSchemaProvider::class.java).schema("type Query {}")
                } as DuplicateEntityFetcherException

            assertThat(
                exception.message,
            ).contains(
                "Duplicate EntityFetcherResolver found for entity type Movie",
                "com.netflix.graphql.dgs.DuplicateEntityFetcherTest\$EntityFetcherConfig.movieEntityFetcher",
                "com.netflix.graphql.dgs.DuplicateEntityFetcherTest\$EntityFetcherConfig.anotherMovieEntityFetcher",
            )
            assertThat(exception.entityType).isEqualTo("Movie")
            assertThat(exception.firstEntityFetcherClass).isEqualTo(EntityFetcherConfig::class.java)

            // The order in which the methods are found can vary, so put the methods in a set to assert
            val methods = setOf(exception.firstEntityFetcherMethod.name, exception.secondEntityFetcherMethod.name)
            assertThat(exception.secondEntityFetcherClass).isEqualTo(EntityFetcherConfig::class.java)
            assertThat(methods).contains("movieEntityFetcher", "anotherMovieEntityFetcher")
        }
    }

    @DgsComponent
    class EntityFetcherConfig {
        @DgsEntityFetcher(name = "Movie")
        fun movieEntityFetcher(values: Map<String, Any>): Movie = Movie()

        @DgsEntityFetcher(name = "Movie")
        fun anotherMovieEntityFetcher(values: Map<String, Any>): Movie = Movie()
    }
}
