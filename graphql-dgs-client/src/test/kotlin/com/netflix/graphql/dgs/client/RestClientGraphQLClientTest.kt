/*
 * Copyright 2023 Netflix, Inc.
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
import com.netflix.graphql.dgs.subscriptions.graphql.sse.DgsGraphQLSSEAutoConfig
import com.netflix.graphql.dgs.webmvc.autoconfigure.DgsWebMvcAutoConfiguration
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestClient

@SpringBootTest(
    classes = [
        DgsAutoConfiguration::class,
        DgsWebMvcAutoConfiguration::class,
        RestClientGraphQLClientTest.TestApp::class
    ],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EnableAutoConfiguration(exclude = [DgsGraphQLSSEAutoConfig::class])
class RestClientGraphQLClientTest {

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @LocalServerPort
    lateinit var port: Integer

    @Autowired
    lateinit var restClientBuilder: RestClient.Builder

    lateinit var restClient: RestClient

    @BeforeEach
    fun setup() {
        restClient = restClientBuilder.baseUrl("http://localhost:$port/graphql").build()
    }

    @Test
    fun `Successful graphql response`() {
        val client = RestClientGraphQLClient(restClient)
        val result = client.executeQuery("{hello}").extractValue<String>("hello")

        assertThat(result).isEqualTo("Hi!")
    }

    @Test
    fun `Extra header can be provided`() {
        val client = RestClientGraphQLClient(restClient) { headers ->
            headers.add("myheader", "test")
        }

        val result = client.executeQuery("{withHeader}").extractValue<String>("withHeader")
        assertThat(result).isEqualTo("Header value: test")
    }

    @Test
    fun `Request parameters can be added, per request`() {
        restClient = restClientBuilder.baseUrl("http://localhost:$port/graphql?q1=one").build()
        val client = RestClientGraphQLClient(restClient)
        val result = client.executeQuery(
            query = "{ withUriParam }"
        ).extractValue<String>("withUriParam")

        assertThat(result).isEqualTo("Parameter q1: one")
    }

    @Test
    fun `Graphql errors should be handled`() {
        val client = RestClientGraphQLClient(restClient)
        val errors = client.executeQuery("{error}").errors

        assertThat(errors).hasSize(1)
        assertThat(errors).first().extracting("message").isEqualTo("java.lang.RuntimeException: Broken!")
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

            @DgsQuery
            fun withHeader(@RequestHeader myheader: String): String {
                return "Header value: $myheader"
            }

            @DgsQuery
            fun withUriParam(@RequestParam("q1") param: String): String {
                return "Parameter q1: $param"
            }

            @DgsTypeDefinitionRegistry
            fun typeDefinitionRegistry(): TypeDefinitionRegistry {
                val schemaParser = SchemaParser()

                @Language("graphql")
                val gqlSchema = """
                type Query{
                    hello: String 
                    withHeader: String
                    withUriParam: String
                    error: String
                }
                """.trimMargin()
                return schemaParser.parse(gqlSchema)
            }
        }
    }
}
