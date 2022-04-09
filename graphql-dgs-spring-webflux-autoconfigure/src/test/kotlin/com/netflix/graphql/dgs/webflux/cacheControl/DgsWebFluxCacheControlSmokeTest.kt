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

package com.netflix.graphql.dgs.webflux.cacheControl

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
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.config.EnableWebFlux
import java.time.Duration

@SpringBootTest(
    properties = [
        "debug:true",
        "dgs.graphql.cache-control.enabled:true"
    ]
)
@EnableWebFlux
@AutoConfigureWebTestClient
@Import(DgsWebFluxAutoConfiguration::class)
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DgsWebFluxCacheControlSmokeTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    @Order(0)
    fun `The demo app is able to start`() {
    }

    @Test
    @Order(1)
    fun `CacheControl WebFlux Smoke test no caching before`() {
        noCacheRequest()
    }

    @Test
    @Order(2)
    fun `CacheControl WebFlux Smoke test with caching`() {
        webTestClient
            .post()
            .uri("/graphql")
            .bodyValue(
                """
                  |{
                  |    "query": "{ product { upc } }"
                  | }
                  |""".trimMargin()
            )
            .exchange()
            .expectStatus().isOk()
            .expectHeader()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60L)).cachePublic()) // WebFlux = public, WebMvc = private
    }

    @Test
    @Order(3)
    fun `CacheControl WebFlux Smoke test no caching after`() {
        noCacheRequest()
    }

    private fun noCacheRequest() {
        webTestClient
            .post()
            .uri("/graphql")
            .bodyValue(
                """
                  |{
                  |    "query": "{ noCache }"
                  | }
                  |""".trimMargin()
            )
            .exchange()
            .expectStatus().isOk()
            .expectHeader()
            .doesNotExist(HttpHeaders.CACHE_CONTROL)
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
                |type Product @cacheControl(maxAge: 60) {
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
