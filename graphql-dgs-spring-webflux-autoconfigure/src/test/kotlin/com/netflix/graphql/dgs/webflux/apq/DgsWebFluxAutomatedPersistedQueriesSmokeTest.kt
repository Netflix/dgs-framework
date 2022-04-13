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

package com.netflix.graphql.dgs.webflux.apq

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.webflux.autoconfiguration.DgsWebFluxAutoConfiguration
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.config.EnableWebFlux

@SpringBootTest(
    properties = [
        "debug:true",
        "dgs.graphql.apq.enabled:true"
    ]
)
@EnableWebFlux
@AutoConfigureWebTestClient
@Import(DgsWebFluxAutoConfiguration::class)
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DgsWebFluxAutomatedPersistedQueriesSmokeTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    @Order(0)
    fun `The demo app is able to start`() {
    }

    @Test
    @Order(1)
    fun `Attempt to execute a POST Request with a known hash`() {
        webTestClient
            .post()
            .uri("/graphql")
            .bodyValue(
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
            .exchange()
            .expectStatus().isOk()
            .expectBody().json(
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
    }

    @Test
    @Order(2)
    fun `Execute a POST Request with a known hash and query`() {
        webTestClient
            .post()
            .uri("/graphql")
            .bodyValue(
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
            .exchange()
            .expectStatus().isOk
            .expectBody().json(
                """
                  | {
                  |    "data": {
                  |        "__typename":"Query"
                  |    }
                  | }
                  |""".trimMargin()
            )
    }

    @Test
    @Order(3)
    fun `Execute a POST Request with a known hash once the query was registered`() {
        webTestClient
            .post()
            .uri("/graphql")
            .bodyValue(
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
            .exchange()
            .expectStatus().isOk
            .expectBody().json(
                """
                  | {
                  |    "data": {
                  |        "__typename":"Query"
                  |    }
                  | }
                  |""".trimMargin()
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
