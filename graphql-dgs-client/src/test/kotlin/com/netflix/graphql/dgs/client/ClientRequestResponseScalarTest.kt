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

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.DgsRuntimeWiring
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.test.EnableDgsTest
import graphql.scalars.ExtendedScalars
import graphql.scalars.country.code.CountryCode
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalTime
import java.util.UUID

/**
 * A test for running E2E graphQL request/response tests using the DGS framework in kotlin
 * This test is to explicitly validate that scalar maps and variables passed in as inputs
 * are serialized and deserialized correctly in the request and response using our provided
 * clients.
 */
@Suppress("GraphQLUnresolvedReference")
@SpringBootTest(
    classes = [
        ClientRequestResponseScalarTest.TestApp::class,
    ],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@EnableDgsTest
class ClientRequestResponseScalarTest {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @LocalServerPort
    lateinit var port: Integer

    @Autowired
    lateinit var restClientBuilder: RestClient.Builder

    @Test
    fun `WebClient - Scalars can be provided`() {
        val client =
            MonoGraphQLClient.createWithWebClient(
                WebClient.create("http://localhost:$port/graphql"),
                options =
                    GraphQLRequestOptions(
                        scalars = mapOf(UUID::class.java to ExtendedScalars.UUID.coercing),
                    ),
            )
        val randomUUID = UUID.randomUUID()
        val query = "query UUIDQuery(${'$'}input: UUID!) { echoUUID(uuid: ${'$'}input) }"
        val result = client.reactiveExecuteQuery(query, mapOf("input" to randomUUID)).block() ?: fail("null response")
        assertThat(result.extractValueAsObject("echoUUID", UUID::class.java)).isEqualTo(randomUUID)
    }

    @Test
    fun `WebClient - Extra Scalars and headers can be provided`() {
        val options =
            GraphQLRequestOptions(
                scalars =
                    mapOf(
                        UUID::class.java to ExtendedScalars.UUID.coercing,
                        LocalTime::class.java to ExtendedScalars.LocalTime.coercing,
                    ),
            )
        val client =
            MonoGraphQLClient.createWithWebClient(
                WebClient.create("http://localhost:$port/graphql"),
                { headers ->
                    headers.add("myheader", "test")
                },
                options,
            )
        val randomUUID = UUID.randomUUID()
        val idQuery = "query UUIDQuery(${'$'}input: UUID!) { echoUUIDWithHeader(uuid: ${'$'}input) }"
        val idResult = client.reactiveExecuteQuery(idQuery, mapOf("input" to randomUUID)).block() ?: fail("null response")
        assertThat(idResult.extractValueAsObject("echoUUIDWithHeader", UUID::class.java)).isEqualTo(randomUUID)

        val time = LocalTime.of(11, 30, 0)
        val timeQuery = "query TimeQuery(${'$'}input: LocalTime!) { echoTime(time: ${'$'}input) }"
        val timeResult = client.reactiveExecuteQuery(timeQuery, mapOf("input" to time)).block() ?: fail("null response")
        assertThat(timeResult.extractValueAsObject("echoTime", LocalTime::class.java)).isEqualTo(time)
    }

    @Test
    fun `RestClient - Scalars can be provided`() {
        val client = restClientBuilder.baseUrl("http://localhost:$port/graphql").build()
        val options =
            GraphQLRequestOptions(
                scalars =
                    mapOf(
                        CountryCode::class.java to ExtendedScalars.CountryCode.coercing,
                        LocalTime::class.java to ExtendedScalars.LocalTime.coercing,
                    ),
            )
        val restClient =
            RestClientGraphQLClient(
                client,
                options,
            )

        val country = CountryCode.HU
        val countryResult =
            restClient.executeQuery(
                "query CountryQuery(${'$'}input: CountryCode!) { echoCountry(code: ${'$'}input) }",
                mapOf("input" to country),
            )
        assertThat(countryResult.extractValueAsObject("echoCountry", CountryCode::class.java)).isEqualTo(country)

        val time = LocalTime.of(11, 30, 0)
        val timeResult =
            restClient.executeQuery(
                "query TimeQuery(${'$'}input: LocalTime!) { echoTime(time: ${'$'}input) }",
                mapOf("input" to time),
            )
        assertThat(timeResult.extractValueAsObject("echoTime", LocalTime::class.java)).isEqualTo(time)
    }

    @SpringBootApplication
    internal open class TestApp {
        @DgsComponent
        class ScalarDataFetcher {
            private val logger = LoggerFactory.getLogger(ScalarDataFetcher::class.java)

            @DgsQuery
            fun echoUUID(
                @InputArgument uuid: UUID,
            ): UUID = uuid

            @DgsQuery
            fun echoTime(
                @InputArgument time: LocalTime,
            ): LocalTime = time

            @DgsQuery
            fun echoUUIDWithHeader(
                @RequestHeader myHeader: String,
                @InputArgument uuid: UUID,
            ): UUID {
                logger.info("Header value = {}", myHeader)
                return uuid
            }

            @DgsQuery
            fun echoCountry(
                @InputArgument code: CountryCode,
            ): CountryCode = code

            @DgsRuntimeWiring
            fun addScalar(builder: RuntimeWiring.Builder): RuntimeWiring.Builder =
                builder.scalar(ExtendedScalars.UUID).scalar(ExtendedScalars.CountryCode).scalar(ExtendedScalars.LocalTime)

            @DgsTypeDefinitionRegistry
            fun typeDefinitionRegistry(): TypeDefinitionRegistry {
                val schemaParser = SchemaParser()

                @Language("graphql")
                val gqlSchema =
                    """
                scalar UUID
                scalar CountryCode
                scalar LocalTime
                type Query {
                    echoCountry(code: CountryCode): CountryCode
                    echoUUID(uuid: UUID): UUID
                    echoUUIDWithHeader(uuid: UUID): UUID
                    echoTime(time: LocalTime): LocalTime
                }
                    """.trimMargin()
                return schemaParser.parse(gqlSchema)
            }
        }
    }
}
