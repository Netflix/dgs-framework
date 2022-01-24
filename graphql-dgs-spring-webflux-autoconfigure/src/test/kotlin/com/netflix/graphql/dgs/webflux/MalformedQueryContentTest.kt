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

package com.netflix.graphql.dgs.webflux

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.reactive.config.EnableWebFlux

@SpringBootTest
@EnableWebFlux
@AutoConfigureWebTestClient
@EnableAutoConfiguration
class MalformedQueryContentTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `Should return a bad request error if the POST request has no content`() {
        webTestClient
            .post()
            .uri("/graphql")
            .bodyValue(" ")
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody<String>()
            .isEqualTo("Invalid query - No content to map to input.")
    }

    @Test
    fun `Should return a bad request error if the POST request has a malformed query`() {
        webTestClient
            .post()
            .uri("/graphql")
            .bodyValue("{")
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody<String>()
            .consumeWith { assertThat(it.responseBody).startsWith("Invalid query -") }
    }

    @Test
    fun `Should return a GraphQL Error if the query is empty`() {
        webTestClient
            .post()
            .uri("/graphql")
            .bodyValue("{ }")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .json(
                """
                {
                  "errors":[
                    {
                      "message": "The query is null or empty.",
                      "locations":[],
                      "extensions":{"errorType":"BAD_REQUEST"}
                    }
                  ]
                }    
                """.trimIndent()
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
                return SchemaParser().parse("type Query{ }")
            }
        }
    }
}
