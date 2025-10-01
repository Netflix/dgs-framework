/*
 * Copyright 2025 Netflix, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.DgsRuntimeWiring
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.test.EnableDgsTest
import graphql.GraphQLContext
import graphql.scalars.ExtendedScalars
import graphql.schema.Coercing
import graphql.schema.CoercingSerializeException
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.netty.handler.logging.LogLevel
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.test.LocalServerPort
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.transport.logging.AdvancedByteBufFormat
import reactor.test.StepVerifier
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Suppress("GraphQLUnresolvedReference")
@SpringBootTest(
    classes = [
        WebClientGraphQLClientTest.TestApp::class,
    ],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@EnableDgsTest
class WebClientGraphQLClientTest {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @LocalServerPort
    lateinit var port: Integer

    @Test
    fun `Successful graphql response`() {
        val client = MonoGraphQLClient.createWithWebClient(WebClient.create("http://localhost:$port/graphql"))
        val result = client.reactiveExecuteQuery("{hello}").map { r -> r.extractValue<String>("hello") }

        StepVerifier
            .create(result)
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

        StepVerifier
            .create(result)
            .expectNext("Header value: test")
            .verifyComplete()
    }

    @Test
    fun `Request parameters can be added, per request`() {
        val httpClient: HttpClient =
            HttpClient
                .create()
                .wiretap(
                    "reactor.netty.http.client.HttpClient",
                    LogLevel.INFO,
                    AdvancedByteBufFormat.TEXTUAL,
                )

        val webClient =
            WebClient
                .builder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .baseUrl("http://localhost:$port/graphql")
                .build()

        val client = MonoGraphQLClient.createWithWebClient(webClient)
        val result =
            client
                .reactiveExecuteQuery(
                    query = "{ withUriParam }",
                    requestBodyUriCustomizer = {
                        it.uri { uriBuilder ->
                            uriBuilder
                                .queryParam("q1", "one")
                                .build()
                        }
                    },
                ).map { r -> r.extractValue<String>("withUriParam") }

        StepVerifier
            .create(result)
            .expectNext("Parameter q1: one")
            .verifyComplete()
    }

    @Test
    fun `Graphql errors should be handled`() {
        val client = MonoGraphQLClient.createWithWebClient(WebClient.create("http://localhost:$port/graphql"))
        val errors = client.reactiveExecuteQuery("{error}").map { r -> r.errors }

        StepVerifier
            .create(errors)
            .expectNextMatches { it.size == 1 && it[0].message.contains("Broken!") }
            .verifyComplete()
    }

    @Test
    fun `Custom ObjectMapper can be supplied to the client`() {
        val mapper: ObjectMapper =
            Jackson2ObjectMapperBuilder
                .json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build()
        val now = LocalDateTime.parse("2024-12-12T12:12:12.12")
        val client =
            MonoGraphQLClient.createWithWebClient(
                webClient = WebClient.create("http://localhost:$port/graphql"),
                objectMapper = mapper,
            )
        val result =
            client
                .reactiveExecuteQuery(
                    "query TimeQuery(${'$'}input: DateTime!) { echoTime(time: ${'$'}input) }",
                    mapOf("input" to now),
                ).block() ?: fail("null response")

        assertThat(result.extractValueAsObject("echoTime", LocalDateTime::class.java)).isEqualTo(now)
    }

    @SpringBootApplication
    internal open class TestApp {
        @DgsComponent
        class SubscriptionDataFetcher {
            @DgsQuery
            fun hello(): String = "Hi!"

            @DgsQuery
            fun error(): String = throw RuntimeException("Broken!")

            @DgsQuery
            fun withHeader(
                @RequestHeader myheader: String,
            ): String = "Header value: $myheader"

            @DgsQuery
            fun withUriParam(
                @RequestParam("q1") param: String,
            ): String = "Parameter q1: $param"

            @DgsQuery
            fun echoTime(
                @InputArgument time: LocalDateTime,
            ): LocalDateTime = time

            @DgsRuntimeWiring
            fun addScalar(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
                return builder.scalar(
                    ExtendedScalars.DateTime.transform {
                        it.coercing(
                            object : Coercing<LocalDateTime, String> {
                                override fun parseValue(
                                    input: Any,
                                    graphQLContext: GraphQLContext,
                                    locale: Locale,
                                ): LocalDateTime? = LocalDateTime.parse(input.toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                                override fun serialize(
                                    dataFetcherResult: Any,
                                    graphQLContext: GraphQLContext,
                                    locale: Locale,
                                ): String? {
                                    if (dataFetcherResult is LocalDateTime) {
                                        return dataFetcherResult.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                    }
                                    throw CoercingSerializeException()
                                }
                            },
                        )
                    },
                )
            }

            @DgsTypeDefinitionRegistry
            fun typeDefinitionRegistry(): TypeDefinitionRegistry {
                val schemaParser = SchemaParser()

                @Language("graphql")
                val gqlSchema =
                    """
                scalar DateTime
                type Query {
                    hello: String 
                    withHeader: String
                    withUriParam: String
                    error: String
                    echoTime(time: DateTime): DateTime
                }
                    """.trimMargin()
                return schemaParser.parse(gqlSchema)
            }
        }
    }
}
