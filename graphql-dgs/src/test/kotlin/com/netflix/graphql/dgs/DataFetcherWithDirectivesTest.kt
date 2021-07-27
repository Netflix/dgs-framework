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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import java.util.*

@ExtendWith(MockKExtension::class)
class DataFetcherWithDirectivesTest {
    @MockK
    lateinit var applicationContextMock: ApplicationContext

    @Test
    fun addFetchersWithConvertedArguments() {

        val queryFetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun hellOFetcher(dataFetchingEnvironment: DgsDataFetchingEnvironment): String {
                val directive = dataFetchingEnvironment.fieldDefinition.directives[0]
                return "hello ${directive.arguments[0].argumentValue}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", queryFetcher))
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = provider.schema(
            """
            directive @someDirective(name: String) on FIELD_DEFINITION
            type Query {
                hello: String @someDirective(name: "some name")
            }
            
            """.trimIndent()
        )

        val build = GraphQL.newGraphQL(schema).build()
        val executionResult = build.execute("{ hello }")
        val data: Map<String, String> = executionResult.getData()
        assertThat(data["hello"]).isEqualTo("hello some name")
    }
}
