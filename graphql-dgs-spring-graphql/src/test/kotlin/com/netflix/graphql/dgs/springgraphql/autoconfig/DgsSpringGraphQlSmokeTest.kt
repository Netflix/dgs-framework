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
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration
import org.springframework.boot.autoconfigure.graphql.servlet.GraphQlWebMvcAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest(
    classes = [
        DgsSpringGraphQlSmokeTest.TestApp::class,
        DgsSpringGraphQLAutoConfiguration::class,
        DgsAutoConfiguration::class,
        DgsSpringGraphQLSourceAutoConfiguration::class,
        GraphQlAutoConfiguration::class,
        GraphQlWebMvcAutoConfiguration::class,
        WebMvcAutoConfiguration::class
    ],

    properties = ["dgs.graphql.schema-locations=classpath:/dgs-spring-graphql-smoke-test.graphqls"]
)
@AutoConfigureMockMvc
class DgsSpringGraphQlSmokeTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun testGraphQlRequest() {
        val query = """
            query {
                dgsField
                springControllerField              
            }
        """.trimIndent()

        data class GraphQlRequest(val query: String)

        mockMvc.post("/graphql") {
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
                    """.trimMargin()
                )
            }
        }
    }

    @TestConfiguration
    open class TestApp {
        @DgsComponent
        open class DgsTestDatafetcher {
            @DgsQuery
            fun dgsField(): String {
                return "test from DGS"
            }
        }

        @Controller
        open class SpringDatafetcher {
            @QueryMapping
            fun springControllerField(): String {
                return "test from Spring Controller"
            }
        }
    }
}
