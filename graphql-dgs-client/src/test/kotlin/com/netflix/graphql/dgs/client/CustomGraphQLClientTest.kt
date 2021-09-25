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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate

@SpringBootTest(
    classes = [DgsAutoConfiguration::class, DgsWebMvcAutoConfiguration::class, WebClientGraphQLClientTest.TestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class CustomGraphQLClientTest {

    @LocalServerPort
    lateinit var port: Integer

    private val restTemplate = RestTemplate()
    lateinit var client: GraphQLClient

    @BeforeEach
    fun before() {
        client = GraphQLClient.createCustom("http://localhost:$port/graphql") { url, headers, body ->
            val httpHeaders = HttpHeaders()
            headers.forEach { httpHeaders.addAll(it.key, it.value) }

            val exchange = restTemplate.exchange(url, HttpMethod.POST, HttpEntity(body, httpHeaders), String::class.java)
            HttpResponse(exchange.statusCodeValue, exchange.body)
        }
    }

    @Test
    fun `Successful graphql response`() {
        val result = client.executeQuery("{hello}").extractValue<String>("hello")
        assertThat(result).isEqualTo("Hi!")
    }

    @Test
    fun `Graphql errors should be handled`() {
        val error = client.executeQuery("{error}").errors[0]
        assertThat(error.message).contains("Broken!")
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
