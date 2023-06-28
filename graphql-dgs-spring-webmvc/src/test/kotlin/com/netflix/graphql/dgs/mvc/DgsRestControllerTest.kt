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

package com.netflix.graphql.dgs.mvc

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import graphql.ExecutionResultImpl
import graphql.execution.reactive.SubscriptionPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(DgsRestController::class)
class DgsRestControllerTest {

    @SpringBootApplication
    open class App

    @MockBean
    lateinit var dgsQueryExecutor: DgsQueryExecutor

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `Is able to execute a a well formed query`() {
        val queryString = "query { hello }"

        `when`(dgsQueryExecutor.execute(eq(queryString), eq(emptyMap()), any(), any(), any(), any())).thenReturn(
            ExecutionResultImpl.newExecutionResult().data(mapOf("hello" to "hello")).build()
        )

        mvc.post("/graphql") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("query" to queryString))
        }.andExpect {
            status { isOk() }
            content {
                jsonPath("errors") {
                    doesNotExist()
                }
                jsonPath("data") {
                    isMap()
                }
                jsonPath("data.hello") {
                    value("hello")
                }
            }
        }
    }

    @Test
    fun `Is able to execute a a well formed query with null variables and extension`() {
        val queryString = "query(\$stranger:String) {hello(name: \$stranger)}"

        `when`(
            dgsQueryExecutor.execute(
                eq(queryString),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(
            ExecutionResultImpl.newExecutionResult().data(mapOf("hello" to "hello")).build()
        )

        mvc.post("/graphql") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("query" to queryString, "variables" to null))
        }.andExpect {
            status { isOk() }
            content {
                jsonPath("errors") {
                    doesNotExist()
                }
                jsonPath("data") {
                    isMap()
                }
                jsonPath("data.hello") {
                    value("hello")
                }
            }
        }
    }

    @Test
    fun `Passing a query with an operationName should execute the matching named query`() {
        val queryString = "query operationA{ hello } query operationB{ hi }"
        val captor = ArgumentCaptor.forClass(String::class.java)
        `when`(dgsQueryExecutor.execute(eq(queryString), eq(emptyMap()), any(), any(), captor.capture(), any())).thenReturn(
            ExecutionResultImpl.newExecutionResult().data(mapOf("hi" to "there")).build()
        )

        mvc.post("/graphql") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("query" to queryString, "operationName" to "operationB"))
        }.andExpect {
            status { isOk() }
            content {
                jsonPath("errors") {
                    doesNotExist()
                }
                jsonPath("data") {
                    isMap()
                }
                jsonPath("data.hi") {
                    value("there")
                }
            }
        }
        assertEquals("operationB", captor.value)
    }

    @Test
    fun `Content-type application graphql should be handled correctly`() {
        val queryString = "{ hello }"

        `when`(dgsQueryExecutor.execute(eq(queryString), eq(emptyMap()), any(), any(), any(), any())).thenReturn(
            ExecutionResultImpl.newExecutionResult().data(mapOf("hello" to "hello")).build()
        )

        mvc.post("/graphql") {
            contentType = MediaType.parseMediaType("application/graphql")
            content = queryString
        }.andExpect {
            status { isOk() }
            content {
                jsonPath("errors") {
                    doesNotExist()
                }
                jsonPath("data") {
                    isMap()
                }
                jsonPath("data.hello") {
                    value("hello")
                }
            }
        }
    }

    @Test
    fun `Return an error when a Subscription is attempted on the Graphql Endpoint`() {
        val queryString = "subscription { stocks { name } }"

        `when`(dgsQueryExecutor.execute(eq(queryString), eq(emptyMap()), any(), any(), any(), any())).thenReturn(
            ExecutionResultImpl.newExecutionResult().data(SubscriptionPublisher(null, null)).build()
        )

        mvc.post("/graphql") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("query" to queryString))
        }.andExpect {
            status { isBadRequest() }
            content {
                string("Trying to execute subscription on /graphql. Use /subscriptions instead!")
            }
        }
    }

    @Test
    fun `Returns a request error if the no body is present`() {
        mvc.post("/graphql") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `Writes response headers when dgs-response-headers are set in extensions object`() {
        val queryString = "query { hello }"

        `when`(dgsQueryExecutor.execute(eq(queryString), eq(emptyMap()), any(), any(), any(), any())).thenReturn(
            ExecutionResultImpl.newExecutionResult().data(mapOf("hello" to "hello"))
                .extensions(mapOf(DgsRestController.DGS_RESPONSE_HEADERS_KEY to mapOf("myHeader" to "hello")))
                .build()
        )

        mvc.post("/graphql") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("query" to queryString))
        }.andExpect {
            status { isOk() }
            header {
                string("myHeader", "hello")
            }
            content {
                jsonPath("errors") {
                    doesNotExist()
                }
                jsonPath("extensions") {
                    doesNotExist()
                }
                jsonPath("data") {
                    isMap()
                }
                jsonPath("data.hello") {
                    value("hello")
                }
            }
        }
    }

    @Test
    fun `Writes response headers when dgs-response-headers are set in extensions object with additional extensions`() {
        val queryString = "query { hello }"

        `when`(dgsQueryExecutor.execute(eq(queryString), eq(emptyMap()), any(), any(), any(), any())).thenReturn(
            ExecutionResultImpl.newExecutionResult().data(mapOf("hello" to "hello"))
                .extensions(mapOf("foo" to "bar", DgsRestController.DGS_RESPONSE_HEADERS_KEY to mapOf("myHeader" to "hello")))
                .build()
        )

        mvc.post("/graphql") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("query" to queryString))
        }.andExpect {
            status { isOk() }
            header {
                string("myHeader", "hello")
            }
            content {
                jsonPath("errors") {
                    doesNotExist()
                }
                jsonPath("extensions") {
                    isMap()
                }
                jsonPath("extensions.foo") {
                    value("bar")
                }
                jsonPath("data") {
                    isMap()
                }
                jsonPath("data.hello") {
                    value("hello")
                }
            }
        }
    }
}
