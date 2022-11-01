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
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.netty.handler.logging.LogLevel
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.transport.logging.AdvancedByteBufFormat
import reactor.test.StepVerifier

@Suppress("GraphQLUnresolvedReference")
@SpringBootTest(
    classes = [
        DgsAutoConfiguration::class,
        DgsWebMvcAutoConfiguration::class,
        WebClientGraphQLClientTest.TestApp::class
    ],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class WebClientGraphQLClientTest {

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @LocalServerPort
    lateinit var port: Integer

    @Test
    fun `Successful graphql response`() {
        val client = MonoGraphQLClient.createWithWebClient(WebClient.create("http://localhost:$port/graphql"))
        val result = client.reactiveExecuteQuery("{hello}").map { r -> r.extractValue<String>("hello") }

        StepVerifier.create(result)
            .expectNext("Hi!")
            .verifyComplete()
    }

    @Test
    fun `Extra header can be provided`() {
        val client =
            MonoGraphQLClient.createWithWebClient(WebClient.create("http://localhost:$port/graphql")) { headers ->
                headers.add("myheader", "test")
            }
        val result = client.reactiveExecuteQuery("{withHeader}").map { r -> r.extractValue<String>("withHeader") }

        StepVerifier.create(result)
            .expectNext("Header value: test")
            .verifyComplete()
    }

    @Test
    fun `Request parameters can be added, per request`() {
        val httpClient: HttpClient = HttpClient
            .create()
            .wiretap(
                "reactor.netty.http.client.HttpClient",
                LogLevel.INFO,
                AdvancedByteBufFormat.TEXTUAL
            )

        val webClient =
            WebClient.builder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .baseUrl("http://localhost:$port/graphql")
                .build()

        val client = MonoGraphQLClient.createWithWebClient(webClient)
        val result = client.reactiveExecuteQuery(
            query = "{ withUriParam }",
            requestBodyUriCustomizer = {
                it.uri { uriBuilder ->
                    uriBuilder
                        .queryParam("q1", "one")
                        .build()
                }
            }
        ).map { r -> r.extractValue<String>("withUriParam") }

        StepVerifier.create(result)
            .expectNext("Parameter q1: one")
            .verifyComplete()
    }

    @Test
    fun `Graphql errors should be handled`() {
        val client = MonoGraphQLClient.createWithWebClient(WebClient.create("http://localhost:$port/graphql"))
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
