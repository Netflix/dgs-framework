/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.graphql.dgs.example.shared.datafetcher

import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.example.ExampleSpringBootTest
import com.netflix.graphql.types.errors.TypedGraphQLError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@ExampleSpringBootTest
class MutationTest {

    @Autowired
    lateinit var dgsQueryExecutor: DgsQueryExecutor

    @Test
    fun testMutation() {
        val executionResult = dgsQueryExecutor.execute(
            """
            mutation {
                addRating(input: {stars: 5, title: "Stranger Things"}) {
                    avgStars
                }
            }
            """.trimIndent()
        )

        assertThat(executionResult.errors.size).isEqualTo(0)
    }

    @Test
    fun testInvalidMutation() {
        val executionResult = dgsQueryExecutor.execute(
            """
            mutation {
                addRating(input: {stars: -1, title: "Stranger Things"}) {
                    avgStars
                }
            }
            """.trimIndent()
        )

        assertThat(executionResult.errors.size).isEqualTo(1)
        assertThat(executionResult.errors[0].message).isEqualTo("java.lang.IllegalArgumentException: Stars must be 1-5")

        assertThat(executionResult.errors[0]).isInstanceOf(TypedGraphQLError::class.java)
        assertThat(executionResult.errors[0].extensions["errorType"]).isEqualTo("INTERNAL")
    }
}
