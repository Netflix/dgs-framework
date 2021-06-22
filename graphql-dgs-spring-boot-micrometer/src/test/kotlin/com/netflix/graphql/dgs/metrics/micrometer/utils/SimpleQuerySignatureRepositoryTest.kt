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

package com.netflix.graphql.dgs.metrics.micrometer.utils

import com.netflix.graphql.dgs.metrics.micrometer.DgsGraphQLMicrometerAutoConfiguration
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.language.Document
import graphql.parser.Parser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.util.AopTestUtils

@ExtendWith(SpringExtension::class)
@SpringBootTest(
    properties = [
        "management.metrics.dgs-graphql.caching.enabled:false",
    ],
    classes = [
        DgsGraphQLMicrometerAutoConfiguration.MetricsPropertiesConfiguration::class,
        DgsGraphQLMicrometerAutoConfiguration.MeterRegistryConfiguration::class,
        DgsGraphQLMicrometerAutoConfiguration.QuerySignatureRepositoryConfiguration::class
    ]
)
internal class SimpleQuerySignatureRepositoryTest {

    @Autowired
    private lateinit var repository: QuerySignatureRepository

    private val parameters: InstrumentationExecutionParameters = mock(InstrumentationExecutionParameters::class.java)

    @Test
    fun `A SimpleQuerySignatureRepository is available`() {
        assertThat(repository).isNotNull

        val targetObj: SimpleQuerySignatureRepository =
            AopTestUtils.getUltimateTargetObject(repository)
        assertThat(targetObj).isNotNull
    }

    @Test
    fun `Is able to compute a query signature for a named query`() {
        val document = parseQuery(QUERY)
        Mockito.`when`(parameters.query).thenReturn(QUERY)
        Mockito.`when`(parameters.operation).thenReturn("Foo")

        val optQuerySignature = repository.get(document, parameters)
        val sig = assertThat(optQuerySignature).get()
        sig.extracting { it.value }.satisfies { assertThat(it).isEqualToNormalizingWhitespace(expectedFooDoc) }
        sig.extracting { it.hash }.isEqualTo(expectedFooSigHash)
    }

    @Test
    fun `Is able to compute a query signature for an unnamed query`() {
        val document = parseQuery(QUERY)
        Mockito.`when`(parameters.query).thenReturn(QUERY)
        Mockito.`when`(parameters.operation).thenReturn(null)

        val optQuerySignature = repository.get(document, parameters)
        val sig = assertThat(optQuerySignature).get()
        sig.extracting { it.value }.satisfies { assertThat(it).isEqualToNormalizingWhitespace(expectedAnonDoc) }
        sig.extracting { it.hash }.isEqualTo(expectedAnonSigHash)
    }

    internal companion object {
        val QUERY = """
          query Foo(${'$'}secretVariable : String) {
                fieldA
                fieldB(name: "a name", someVar: ${'$'}secretVariable )
                fieldC {
                    innerFieldA
                    innerFieldB
                    innerFieldC
                } 
                ... X
            }
            query {
                fieldA
                fieldB
            }
        """.trimIndent()

        val expectedFooDoc = """
            query Foo(${'$'}var1: String) {
              fieldA
              fieldB(name: "", someVar: ${'$'}var1)
              fieldC {
                innerFieldA
                innerFieldB
                innerFieldC
              }
              ...X
            }
        """.trimIndent()

        const val expectedFooSigHash = "ab279c4a18bcffc1a5d646dd0295d4cd08f11ff0aaec76db2cc4dab7e7fefb07"

        val expectedAnonDoc = """
            query {
                fieldA
                fieldB
            }  
        """.trimIndent()

        const val expectedAnonSigHash = "0f2648dd1c00c3526e72341d2a5da4593c04f3b8c32ceeacea0238c3850dfb08"

        fun parseQuery(query: String): Document {
            return Parser().parseDocument(query)
        }
    }
}
