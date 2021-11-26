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

import com.netflix.graphql.dgs.*
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.OffsetTime

@SpringBootTest(classes = [TimeScalarsSmokeTest.LocalApp::class])
@EnableAutoConfiguration
internal class TimeScalarsSmokeTest {

    @Autowired
    lateinit var queryExecutor: DgsQueryExecutor

    @Test
    fun date() {
        val data = executeQueryExtractingData<Map<String, String>>(
            """query{ echoDate( date: "2021-07-12") }"""
        )
        assertThat(data).extracting { it["echoDate"] }.isEqualTo("2021-07-12")

        val mData = executeQueryExtractingData<Map<String, String>>(
            """mutation{ echoDateMutation( date: "2021-07-12") }"""
        )
        assertThat(mData).extracting { it["echoDateMutation"] }.isEqualTo("2021-07-12")
    }

    @Test
    fun time() {
        val data = executeQueryExtractingData<Map<String, String>>(
            """{ echoTime( time: "11:30:00.10+02:10") }"""
        )
        assertThat(data).extracting { it["echoTime"] }.isEqualTo("11:30:00.1+02:10")
    }

    @Test
    fun dateTime() {
        val data = executeQueryExtractingData<Map<String, String>>(
            """{ echoDateTime( dateTime: "2021-07-12T11:30:00.10+02:10") }"""
        )
        assertThat(data).extracting { it["echoDateTime"] }.isEqualTo("2021-07-12T11:30:00.1+02:10")

        val mData = executeQueryExtractingData<Map<String, String>>(
            """mutation{ echoDateTimeMutation( dateTime: "2021-07-12T11:30:00.10+02:10") }"""
        )
        assertThat(mData).extracting { it["echoDateTimeMutation"] }.isEqualTo("2021-07-12T11:30:00.1+02:10")
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
                |   echoDate(date: Date): Date
                |   echoTime(time: Time): Time
                |   echoDateTime(dateTime: DateTime): DateTime
                | }
                | 
                | type Mutation{
                |   echoDateMutation(date: Date): Date
                |   echoTimeMutation(time: Time): Time
                |   echoDateTimeMutation(dateTime: DateTime): DateTime
                | }
                | 
                | scalar Date
                | scalar Time
                | scalar DateTime
                """.trimMargin()
                return schemaParser.parse(gqlSchema)
            }

            @DgsQuery
            fun echoDate(@InputArgument date: LocalDate): LocalDate = date

            @DgsMutation
            fun echoDateMutation(@InputArgument date: LocalDate): LocalDate = date

            @DgsQuery
            fun echoTime(@InputArgument time: OffsetTime): OffsetTime = time

            @DgsMutation
            fun echoTimeMutation(@InputArgument time: OffsetTime): OffsetTime = time

            @DgsQuery
            fun echoDateTime(@InputArgument dateTime: OffsetDateTime): OffsetDateTime = dateTime

            @DgsMutation
            fun echoDateTimeMutation(@InputArgument dateTime: OffsetDateTime): OffsetDateTime = dateTime
        }
    }
}
