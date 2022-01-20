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

package com.netflix.graphql.dgs.webmvc.apq

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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "debug:true",
        "dgs.graphql.apq.enabled:true"
    ]
)
@AutoConfigureMockMvc
@EnableAutoConfiguration
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DgsWebMVCAutomatedPersistedQueriesSmokeTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Test
    @Order(0)
    fun `The demo app is able to start`() {
    }

    @Test
    @Order(1)
    fun `Attempt to execute a POST Request with a known hash`() {
        val uriBuilder =
            MockMvcRequestBuilders
                .post("/graphql")
                .content(
                    """
                       |{
                       |    "extensions":{
                       |        "persistedQuery":{
                       |            "version":1,
                       |            "sha256Hash":"ecf4edb46db40b5132295c0291d62fb65d6759a9eedfa4d5d612dd5ec54a6b38"
                       |        }
                       |    }
                       | }
                       |""".trimMargin()
                )
        mvc.perform(uriBuilder)
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    |{
                    |   "errors":[
                    |     {
                    |       "message":"PersistedQueryNotFound",
                    |       "locations":[],
                    |       "extensions":{
                    |         "persistedQueryId":"ecf4edb46db40b5132295c0291d62fb65d6759a9eedfa4d5d612dd5ec54a6b38",
                    |         "generatedBy":"graphql-java",
                    |         "classification":"PersistedQueryNotFound"
                    |       }
                    |     }
                    |   ]
                    | }
                    |""".trimMargin()
                )
            )
    }

    @Test
    @Order(2)
    fun `Execute a POST Request with a known hash and query`() {
        val uriBuilder =
            MockMvcRequestBuilders
                .post("/graphql")
                .content(
                    """
                       |{
                       |    "query": "{__typename}",
                       |    "extensions":{
                       |        "persistedQuery":{
                       |            "version":1,
                       |            "sha256Hash":"ecf4edb46db40b5132295c0291d62fb65d6759a9eedfa4d5d612dd5ec54a6b38"
                       |        }
                       |    }
                       | }
                       |""".trimMargin()
                )
        mvc.perform(uriBuilder)
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    | {
                    |    "data": {
                    |        "__typename":"Query"
                    |    }
                    | }
                    |""".trimMargin()
                )
            )
    }

    @Test
    @Order(3)
    fun `Execute a POST Request with a known hash once the query was registered`() {
        val uriBuilder =
            MockMvcRequestBuilders
                .post("/graphql")
                .content(
                    """
                       |{
                       |    "extensions":{
                       |        "persistedQuery":{
                       |            "version":1,
                       |            "sha256Hash":"ecf4edb46db40b5132295c0291d62fb65d6759a9eedfa4d5d612dd5ec54a6b38"
                       |        }
                       |    }
                       | }
                       |""".trimMargin()
                )
        mvc.perform(uriBuilder)
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    | {
                    |    "data": {
                    |        "__typename":"Query"
                    |    }
                    | }
                    |""".trimMargin()
                )
            )
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
                |type Query{
                |}
                """.trimMargin()
                return schemaParser.parse(gqlSchema)
            }
        }
    }
}
