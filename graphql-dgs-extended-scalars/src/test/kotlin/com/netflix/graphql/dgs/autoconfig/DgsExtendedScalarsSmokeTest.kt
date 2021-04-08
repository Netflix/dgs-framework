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
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneOffset
import java.util.*

@SpringBootTest(classes = [DgsExtendedScalarsSmokeTest.LocalApp::class])
@EnableAutoConfiguration
internal class DgsExtendedScalarsSmokeTest {

    @Autowired
    lateinit var queryExecutor: DgsQueryExecutor

    @Test
    fun `Date & Time scalars are available`() {
        val data = executeQueryExtractingData<Map<String, String>>("{aDate aTime aDateTime }")
        assertThat(data).containsAllEntriesOf(
            mapOf(
                "aDate" to "1963-01-01",
                "aTime" to "18:00:30Z",
                "aDateTime" to "1963-01-01T18:00:30Z"
            )
        )
    }

    @Test
    fun `Object, JSON, Url, and Locale scalars are available`() {
        val data =
            executeQueryExtractingData<Map<String, Any>>("{aJSONObject anObject aURL aLocale}")

        assertThat(data).containsAllEntriesOf(
            mapOf(
                "aJSONObject" to mapOf("name" to "json"),
                "anObject" to mapOf("name" to "object"),
                "aURL" to "http://localhost:8080",
                "aLocale" to "en-US"
            )
        )
    }

    @Test
    fun `Numeric scalars are available`() {
        val data =
            executeQueryExtractingData<Map<String, Number>>(
                """
                |{ 
                |   aPositiveInt
                |   aNegativeInt
                |   aNonPositiveInt
                |   aNonNegativeInt
                |   aPositiveFloat
                |   aNegativeFloat
                |   aNonPositiveFloat
                |   aNonNegativeFloat
                |   aLong
                |   aShort
                |   aByte
                |   aBigDecimal
                |   aBigInteger
                |}
                """.trimMargin()
            )
        assertThat(data).containsAllEntriesOf(
            mapOf(
                "aPositiveInt" to 1,
                "aNegativeInt" to -1,
                "aNonPositiveInt" to 0,
                "aNonNegativeInt" to 0,
                "aPositiveFloat" to 1.0,
                "aNegativeFloat" to -1.0,
                "aNonPositiveFloat" to 0.0,
                "aNonNegativeFloat" to 0.0,
                "aLong" to 9223372036854775807L,
                "aShort" to 32767,
                "aByte" to 127,
                "aBigDecimal" to 10,
                "aBigInteger" to 10
            )
        )
    }

    @Test
    fun `Char scalars are available`() {
        val data =
            executeQueryExtractingData<Map<String, Any>>("{ aChar }")
        assertThat(data).containsAllEntriesOf(mapOf("aChar" to "A"))
    }

    private fun <T> executeQueryExtractingData(query: String): T {
        return queryExecutor.executeAndExtractJsonPath(query, "data")
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
