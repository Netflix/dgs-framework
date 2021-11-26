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
import graphql.schema.DataFetchingEnvironment
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@ExtendWith(MockKExtension::class)
class CustomScalarsTest {
    @MockK
    lateinit var applicationContextMock: ApplicationContext

    @Test
    fun testLocalDateTimeScalar() {
        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "now")
            fun now(): LocalDateTime {
                return LocalDateTime.now()
            }

            @DgsData(parentType = "Query", field = "schedule")
            fun schedule(env: DataFetchingEnvironment): Boolean {
                val time = env.getArgument<LocalDateTime>("time")
                println(time)
                return true
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "timeFetcher",
                fetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns mapOf(
            Pair(
                "localDateTimeScalar",
                LocalDateTimeScalar()
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())

        val schema = provider.schema(
            """
            type Query {
                now: DateTime
                schedule(time: DateTime): Boolean
            }
            
            scalar DateTime
            """.trimIndent()
        )

        val build = GraphQL.newGraphQL(schema).build()
        val executionResult = build.execute(
            """
            {
                now
            }
            """.trimIndent()
        )

        Assertions.assertEquals(0, executionResult.errors.size)
        val data = executionResult.getData<Map<String, String>>()
        Assertions.assertTrue(
            LocalDateTime.parse(data["now"], DateTimeFormatter.ISO_DATE_TIME).plusHours(1).isAfter(LocalDateTime.now())
        )
    }
}
