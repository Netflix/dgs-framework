/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.graphql.dgs.springgraphql.autoconfig

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration
import org.springframework.boot.autoconfigure.graphql.servlet.GraphQlWebMvcAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.execution.SchemaReport
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.function.Consumer

@SpringBootTest(
    classes = [
        DgsSpringGraphQlSmokeTest.TestApp::class,
        DgsSpringGraphQLAutoConfiguration::class,
        DgsAutoConfiguration::class,
        DgsSpringGraphQLSourceAutoConfiguration::class,
        GraphQlAutoConfiguration::class,
        GraphQlWebMvcAutoConfiguration::class,
        WebMvcAutoConfiguration::class,
    ],
    properties = [
        "dgs.graphql.schema-locations=classpath:/dgs-spring-graphql-smoke-test.graphqls",
        "spring.graphql.schema.inspection.enabled=true",
        "dgs.graphql.schema-wiring-validation-enabled=false",
    ],
)
@AutoConfigureMockMvc
class DgsSpringGraphQlSmokeTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var testReportConsumer: TestApp.TestReportConsumer

    @Test
    fun testGraphQlRequest() {
        val query =
            """
            query {
                dgsField
                springControllerField              
            }
            """.trimIndent()

        data class GraphQlRequest(
            val query: String,
        )

        mockMvc
            .post("/graphql") {
                content = jacksonObjectMapper().writeValueAsString(GraphQlRequest(query))
                accept = MediaType.APPLICATION_JSON
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
                content {
                    json(
                        """{
                |  "data": {
                |    "dgsField": "test from DGS",
                |    "springControllerField": "test from Spring Controller"
                |  }
                |}
                        """.trimMargin(),
                    )
                }
            }
    }

    @Test
    fun testSchemaArgumentReporter() {
        assertThat(testReportConsumer.schemaReport?.unmappedArguments()).hasSize(2)
        assertThat(
            testReportConsumer.schemaReport?.unmappedArguments()?.values,
        ).containsExactly(listOf("someArg", "someOtherArg"), listOf("someArg"))
    }

    @TestConfiguration
    open class TestApp {
        @DgsComponent
        open class DgsTestDatafetcher {
            @DgsQuery
            fun dgsField(): String = "test from DGS"

            @DgsQuery
            fun unmappedArgument(
                @InputArgument someArg: String,
                @InputArgument someOtherArg: Int,
            ): String = "unmapped argument test"

            @DgsQuery
            fun incorrectNamedArgument(
                @InputArgument(name = "someArg") somename: String,
            ): String = "unmapped argument test"

            @DgsQuery
            fun mappedArguments(
                @InputArgument firstParam: String,
                @InputArgument secondParam: Int,
            ): String = "mapped argument test"
        }

        @Controller
        open class SpringDatafetcher {
            @QueryMapping
            fun springControllerField(): String = "test from Spring Controller"
        }

        @Component
        open class TestReportConsumer : Consumer<SchemaReport> {
            var schemaReport: SchemaReport? = null

            override fun accept(schemaReport: SchemaReport) {
                this.schemaReport = schemaReport
            }
        }
    }
}
