/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.graphql.dgs.webmvc.cacheControl

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "debug:true",
        "dgs.graphql.cache-control.enabled:true"
    ]
)
@AutoConfigureMockMvc
@EnableAutoConfiguration
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DgsWebMVCCacheControlSmokeTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Test
    @Order(1)
    fun `CacheControl WebMvc Smoke test no caching before`() {
        noCacheRequest()
    }

    @Test
    @Order(1)
    fun `CacheControl WebMvc Smoke test with caching`() {
        val uriBuilder = MockMvcRequestBuilders
            .post("/graphql")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                  |{
                  |    "query": "{ product { upc } }"
                  | }
                  |
                """.trimMargin()
            )
        mvc.perform(uriBuilder)
            .andExpect(status().isOk)
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    "max-age=60, private"
                )
            ) // WebFlux = public, WebMvc = private
    }

    @Test
    @Order(2)
    fun `CacheControl WebMvc Smoke test no caching after`() {
        noCacheRequest()
    }

    private fun noCacheRequest() {
        val uriBuilder = MockMvcRequestBuilders
            .post("/graphql")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                  |{
                  |    "query": "{ noCache }"
                  | }
                  |
                """.trimMargin()
            )
        mvc.perform(uriBuilder)
            .andExpect(status().isOk)
            .andExpect(header().doesNotExist(HttpHeaders.CACHE_CONTROL))
    }

    @SpringBootApplication(proxyBeanMethods = false, scanBasePackages = [])
    @ComponentScan(
        useDefaultFilters = false,
        includeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [])]
    )
    @SuppressWarnings("unused")
    open class LocalApp {

        @DgsComponent
        class ExampleImplementation {

            @DgsTypeDefinitionRegistry
            fun typeDefinitionRegistry(): TypeDefinitionRegistry {
                val schemaParser = SchemaParser()

                val gqlSchema = """
                |enum CacheControlScope {
                |    PUBLIC
                |    PRIVATE
                |}
                |
                |directive @cacheControl(
                |    maxAge: Int
                |    scope: CacheControlScope
                |    inheritMaxAge: Boolean
                |) on FIELD_DEFINITION | OBJECT | INTERFACE | UNION
                |
                |type Product @cacheControl(maxAge: 60 scope: PRIVATE) {
                |    upc: String!
                |    inStock: Boolean
                |    quantity: Int
                |}
                |
                |type Query {
                |    product: Product
                |    noCache: String!
                |}
                """.trimMargin()
                return schemaParser.parse(gqlSchema)
            }
        }
    }
}
