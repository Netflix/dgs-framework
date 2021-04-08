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

package com.netflix.graphql.dgs.autoconfig

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneOffset
import java.util.*

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = [DgsExtendedScalarsSmokeTest.LocalApp::class]
)
@AutoConfigureMockMvc
@EnableAutoConfiguration
internal class DgsExtendedScalarsSmokeTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `Date & Time scalars are available`() {
        assertResponse(
            """{ "query": "{aDate aTime aDateTime}" }""",
            """
                |{
                |   "data": {
                |       "aDate":"1963-01-01",
                |       "aTime":"18:00:30Z",
                |       "aDateTime":"1963-01-01T18:00:30Z"
                |   }
                |}""".trimMargin()
        )
    }

    @Test
    fun `Object, JSON, Url, and Locale scalars are available`() {
        assertResponse(
            """{ "query": "{aJSONObject anObject aURL aLocale}" }""",
            """
                |{
                |   "data": {
                |       "aJSONObject":{"name":"json"},
                |       "anObject":{"name":"object"},
                |       "aURL":"http://localhost:8080",
                |       "aLocale":"en-US"
                |   }
                |}
                |""".trimMargin()
        )
    }

    @Test
    fun `Numeric scalars are available`() {
        assertResponse(
            """
                |{ 
                |   "query": "{
                |       aPositiveInt
                |       aNegativeInt
                |       aNonPositiveInt
                |       aNonNegativeInt
                |       aPositiveFloat
                |       aNegativeFloat
                |       aNonPositiveFloat
                |       aNonNegativeFloat
                |       aLong
                |       aShort
                |       aByte
                |       aBigDecimal
                |       aBigInteger
                |   }"
                |}
                """.trimMargin().replace("\n ", ""),
            """
                |{
                |   "data": {
                |       "aPositiveInt":1,
                |       "aNegativeInt":-1,
                |       "aNonPositiveInt":0,
                |       "aNonNegativeInt":0,
                |       "aPositiveFloat":1.0,
                |       "aNegativeFloat":-1.0,
                |       "aNonPositiveFloat":0.0,
                |       "aNonNegativeFloat":0.0,
                |       "aLong":9223372036854775807,
                |       "aShort":32767,
                |       "aByte":127,
                |       "aBigDecimal":10,
                |       "aBigInteger":10
                |   }
                |}
                |""".trimMargin()
        )
    }

    @Test
    fun `Char scalars are available`() {
        assertResponse(
            """{ "query": "{ aChar }" }""",
            """{ "data": { "aChar": "A" } }"""
        )
    }

    private fun assertResponse(query: String, expectedResponse: String) {
        mvc.perform(
            MockMvcRequestBuilders
                .post("/graphql")
                .content(query)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json(expectedResponse, false))
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @SuppressWarnings("unused")
    open class LocalApp {

        @DgsComponent
        class ExampleImplementation {

            @DgsTypeDefinitionRegistry
            fun typeDefinitionRegistry(): TypeDefinitionRegistry {
                val schemaParser = SchemaParser()

                val gqlSchema = """
                |type Query{
                |   aDate: Date
                |   aTime: Time
                |   aDateTime: DateTime
                |   aJSONObject: JSON
                |   anObject: Object
                |   aURL: Url
                |   aLocale: Locale
                |   aPositiveInt: PositiveInt
                |   aNegativeInt: NegativeInt
                |   aNonPositiveInt: NonPositiveInt
                |   aNonNegativeInt: NonNegativeInt
                |   aPositiveFloat: PositiveFloat
                |   aNegativeFloat: NegativeFloat
                |   aNonPositiveFloat: NonPositiveFloat
                |   aNonNegativeFloat:  NonNegativeFloat
                |   aLong: Long
                |   aShort: Short
                |   aByte: Byte
                |   aBigDecimal: BigDecimal
                |   aBigInteger: BigInteger
                |   aChar: Char
                | }
                | 
                | scalar Date
                | scalar Time
                | scalar DateTime
                | scalar JSON
                | scalar Object
                | scalar Url
                | scalar Locale
                | scalar PositiveInt
                | scalar NegativeInt
                | scalar NonPositiveInt
                | scalar NonNegativeInt
                | scalar PositiveFloat
                | scalar NegativeFloat
                | scalar NonPositiveFloat
                | scalar NonNegativeFloat
                | scalar Long
                | scalar Short
                | scalar Byte
                | scalar BigDecimal
                | scalar BigInteger
                | scalar Char
                """.trimMargin()
                return schemaParser.parse(gqlSchema)
            }

            @DgsQuery
            fun aDate(): LocalDate =
                LocalDate.of(1963, 1, 1)

            @DgsQuery
            fun aTime(): OffsetTime =
                OffsetTime.of(18, 0, 30, 0, ZoneOffset.UTC)

            @DgsQuery
            fun aDateTime(): OffsetDateTime =
                OffsetDateTime.of(1963, 1, 1, 18, 0, 30, 0, ZoneOffset.UTC)

            @DgsQuery
            fun aJSONObject(): SomeData = SomeData("json")

            @DgsQuery
            fun anObject(): SomeData = SomeData("object")

            @DgsQuery
            fun aURL(): URL = URL("http://localhost:8080")

            @DgsQuery
            fun aLocale(): Locale = Locale.US

            @DgsQuery
            fun aPositiveInt(): Int = 1

            @DgsQuery
            fun aNegativeInt(): Int = -1

            @DgsQuery
            fun aNonPositiveInt(): Int = 0

            @DgsQuery
            fun aNonNegativeInt(): Int = 0

            @DgsQuery
            fun aPositiveFloat(): Float = 1.0f

            @DgsQuery
            fun aNegativeFloat(): Float = -1.0f

            @DgsQuery
            fun aNonPositiveFloat(): Float = 0.0f

            @DgsQuery
            fun aNonNegativeFloat(): Float = 0.0f

            @DgsQuery
            fun aLong(): Long = Long.MAX_VALUE

            @DgsQuery
            fun aShort(): Short = Short.MAX_VALUE

            @DgsQuery
            fun aByte(): Byte = Byte.MAX_VALUE

            @DgsQuery
            fun aBigDecimal(): BigDecimal = BigDecimal.TEN

            @DgsQuery
            fun aBigInteger(): BigInteger = BigInteger.TEN

            @DgsQuery
            fun aChar(): Char = 'A'

            data class SomeData(val name: String)
        }
    }
}
