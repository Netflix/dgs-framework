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

package com.netflix.graphql.dgs.client

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import com.netflix.graphql.dgs.webmvc.autoconfigure.DgsWebMvcAutoConfiguration
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.TypeName
import graphql.schema.idl.TypeDefinitionRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.web.reactive.function.client.WebClient
import reactor.test.StepVerifier

@SpringBootTest(
    classes = [DgsAutoConfiguration::class, DgsWebMvcAutoConfiguration::class, WebClientGraphQLClientTest.TestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class WebClientGraphQLClientTest {

    @LocalServerPort
    lateinit var port: Integer
    lateinit var client: WebClientGraphQLClient

    @BeforeEach
    fun setup() {
        client = WebClientGraphQLClient(WebClient.create("http://localhost:$port/graphql"))
    }

    @Test
    fun `Successful graphql response`() {
        val result = client.reactiveExecuteQuery("{hello}").map { r -> r.extractValue<String>("hello") }

        StepVerifier.create(result)
            .expectNext("Hi!")
            .verifyComplete()
    }

    @Test
    fun `Graphql errors should be handled`() {
        val errors = client.reactiveExecuteQuery("{error}").map { r -> r.errors }

        StepVerifier.create(errors)
            .expectNextMatches { it.size == 1 && it[0].message.contains("Broken!") }
            .verifyComplete()
    }

    @SpringBootApplication
    internal open class TestApp {

        @DgsComponent
        class SubscriptionDataFetcher {
            @DgsQuery
            fun hello(): String {
                return "Hi!"
            }

            @DgsQuery
            fun error(): String {
                throw RuntimeException("Broken!")
            }

            @DgsTypeDefinitionRegistry
            fun typeDefinitionRegistry(): TypeDefinitionRegistry {
                val newRegistry = TypeDefinitionRegistry()
                newRegistry.add(
                    ObjectTypeDefinition.newObjectTypeDefinition().name("Query")
                        .fieldDefinition(
                            FieldDefinition.newFieldDefinition()
                                .name("hello")
                                .type(TypeName("String")).build()
                        ).fieldDefinition(
                            FieldDefinition.newFieldDefinition()
                                .name("error")
                                .type(TypeName("String")).build()
                        )
                        .build()
                )
                return newRegistry
            }
        }
    }
}
