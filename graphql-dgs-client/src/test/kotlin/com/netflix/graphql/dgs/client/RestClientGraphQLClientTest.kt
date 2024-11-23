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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.DgsRuntimeWiring
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import com.netflix.graphql.dgs.subscriptions.graphql.sse.DgsGraphQLSSEAutoConfig
import com.netflix.graphql.dgs.webmvc.autoconfigure.DgsWebMvcAutoConfiguration
import graphql.GraphQLContext
import graphql.scalars.ExtendedScalars
import graphql.schema.Coercing
import graphql.schema.CoercingSerializeException
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatException
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@SpringBootTest(
    classes = [
        DgsAutoConfiguration::class,
        DgsWebMvcAutoConfiguration::class,
        RestClientGraphQLClientTest.TestApp::class,
    ],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
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
    fun `Unsuccessful request with default status handling`() {
        val client = RestClientGraphQLClient(restClient.mutate().baseUrl("http://localhost:$port/wrongpath").build())
        assertThatException().isThrownBy { client.executeQuery("{hello}") }.isInstanceOf(HttpClientErrorException::class.java)
    }

    @Test
    fun `Unsuccessful request with non default status handling`() {
        val client =
            RestClientGraphQLClient(
                restClient
                    .mutate()
                    .defaultStatusHandler(
                        { true },
                        { _, _ -> },
                    ).baseUrl("http://localhost:$port/wrongpath")
                    .build(),
            )
        assertThatException().isThrownBy { client.executeQuery("{hello}") }.isInstanceOf(GraphQLClientException::class.java)
    }

    @Test
    fun `Extra header can be provided`() {
        val client =
            RestClientGraphQLClient(restClient) { headers ->
                headers.add("myheader", "test")
            }

        val result = client.executeQuery("{withHeader}").extractValue<String>("withHeader")
        assertThat(result).isEqualTo("Header value: test")
    }

    @Test
    fun `Request parameters can be added, per request`() {
        restClient = restClientBuilder.baseUrl("http://localhost:$port/graphql?q1=one").build()
        val client = RestClientGraphQLClient(restClient)
        val result =
            client
                .executeQuery(
                    query = "{ withUriParam }",
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

    @Test
    fun `Custom ObjectMapper can be supplied to the client`() {
        val mapper: ObjectMapper =
            Jackson2ObjectMapperBuilder
                .json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build()
        val now = LocalDateTime.parse("2024-12-12T12:12:12.12")
        val client = RestClientGraphQLClient(restClient, mapper)
        val result =
            client.executeQuery(
                "query TimeQuery(${'$'}input: DateTime!) { echoTime(time: ${'$'}input) }",
                mapOf("input" to now),
            )
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
