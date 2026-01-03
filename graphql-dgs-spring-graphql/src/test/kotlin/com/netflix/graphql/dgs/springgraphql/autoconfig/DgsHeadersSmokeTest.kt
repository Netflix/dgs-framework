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

package com.netflix.graphql.dgs.springgraphql.autoconfig

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsExecutionResult
import com.netflix.graphql.dgs.DgsQuery
import graphql.ExecutionResult
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.autoconfigure.GraphQlAutoConfiguration
import org.springframework.boot.graphql.autoconfigure.servlet.GraphQlWebMvcAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.concurrent.CompletableFuture

@SpringBootTest(
    classes = [
        DgsHeadersSmokeTest.TestApp::class,
        DgsSpringGraphQLAutoConfiguration::class,
        GraphQlAutoConfiguration::class,
        GraphQlWebMvcAutoConfiguration::class,
        WebMvcAutoConfiguration::class,
    ],
    properties = [
        "dgs.graphql.schema-locations=classpath:/dgs-spring-graphql-smoke-test.graphqls",
    ],
)
@AutoConfigureMockMvc
class DgsHeadersSmokeTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `Response headers can be set by using DgsExecutionResult`() {
        val query =
            """
            query {
                dgsField
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
                header { string("dgsexecutionresultheader", "set from DgsExecutionResult") }
                header { string("extensionheader", "set from extensions") }
            }
    }

    @TestConfiguration
    open class TestApp {
        @DgsComponent
        open class DgsTestDatafetcher {
            @DgsQuery
            fun dgsField(): String = "test from DGS"

            @Component
            open class MyIntrospection : SimplePerformantInstrumentation() {
                override fun instrumentExecutionResult(
                    executionResult: ExecutionResult,
                    parameters: InstrumentationExecutionParameters,
                    state: InstrumentationState?,
                ): CompletableFuture<ExecutionResult> {
                    val headers = HttpHeaders()
                    headers.add("dgsexecutionresultheader", "set from DgsExecutionResult")

                    val extensionHeaders = HttpHeaders()
                    extensionHeaders.add("extensionheader", "set from extensions")
                    val updatedExecutionResult =
                        executionResult.transform { r ->
                            r.extensions(
                                mapOf(
                                    Pair(
                                        "dgs-response-headers",
                                        extensionHeaders,
                                    ),
                                ),
                            )
                        }

                    return CompletableFuture.completedFuture(
                        DgsExecutionResult
                            .builder()
                            .executionResult(updatedExecutionResult)
                            .headers(headers)
                            .build(),
                    )
                }
            }
        }
    }
}
