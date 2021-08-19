/*
 * Copyright 2021 Netflix, Inc.
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

import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import graphql.GraphQL
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import java.util.*

@ExtendWith(MockKExtension::class)
class CustomDirectivesTest {
    @MockK
    lateinit var applicationContextMock: ApplicationContext

    @Test
    fun testCustomDirectives() {
        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun hello(): String = "hello"

            @DgsData(parentType = "Query", field = "word")
            fun word(): String = "abcefg"
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns mapOf()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns mapOf(
            Pair(
                "uppercase",
                UppercaseDirective()
            ),
            Pair(
                "wordfilter",
                WordFilterDirective()
            )
        )

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())

        val schema = provider.schema(
            """
            type Query {
                hello: String @uppercase
                word: String
            }
            
            directive @uppercase on FIELD_DEFINITION
            """.trimIndent()
        )

        val build = GraphQL.newGraphQL(schema).build()
        val executionResult = build.execute(
            """
            {
               hello
            }
            """.trimIndent()
        )

        assertEquals(0, executionResult.errors.size)
        val data = executionResult.getData<Map<String, String>>()
        assertThat(data["hello"]).isEqualTo("HELLO")

        // test global directive
        val wordExecutionResult = build.execute(
            """
            {
               word
            }
            """.trimIndent()
        )

        assertEquals(0, wordExecutionResult.errors.size)
        val wordData = wordExecutionResult.getData<Map<String, String>>()
        assertThat(wordData["word"]).contains("xxx")
    }
}
