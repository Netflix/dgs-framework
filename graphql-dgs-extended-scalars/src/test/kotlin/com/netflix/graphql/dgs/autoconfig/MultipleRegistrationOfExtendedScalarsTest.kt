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
import graphql.scalars.ExtendedScalars
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest(
    classes = [MultipleRegistrationOfExtendedScalarsTest.LocalApp::class]
)
@EnableAutoConfiguration
internal class MultipleRegistrationOfExtendedScalarsTest {

    @Autowired
    lateinit var queryExecutor: DgsQueryExecutor

    @Autowired
    lateinit var timesExtendedScalarsRegistrar: DgsExtendedScalarsAutoConfiguration.ExtendedScalarRegistrar

    @Test
    fun `Multiple registrations of the same scalar will not affect the outcome of the query`() {
        assertThat(timesExtendedScalarsRegistrar).isNotNull

        val dateStr: String = queryExecutor.executeAndExtractJsonPath("""{ aDate }""", "data.aDate")
        assertThat(dateStr).isEqualTo("1963-01-01")
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    open class LocalApp {

        @DgsComponent
        open class ExampleImplementation {

            @DgsTypeDefinitionRegistry
            fun typeDefinitionRegistry(): TypeDefinitionRegistry {
                val gqlSchema = """
                |type Query{ aDate: Date }
                |scalar Date
                """.trimMargin()
                return SchemaParser().parse(gqlSchema)
            }

            @DgsQuery
            fun aDate(): LocalDate = LocalDate.of(1963, 1, 1)

            @DgsRuntimeWiring
            fun manualScalarRegistration(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
                return builder.scalar(ExtendedScalars.Date)
            }
        }
    }
}
