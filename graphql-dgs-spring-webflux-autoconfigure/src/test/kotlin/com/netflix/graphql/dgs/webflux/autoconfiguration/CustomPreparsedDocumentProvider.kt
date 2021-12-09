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

package com.netflix.graphql.dgs.webflux.autoconfiguration

import com.ibm.icu.impl.Assert
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import graphql.ExecutionInput
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.execution.preparsed.PreparsedDocumentProvider
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.TypeName
import graphql.schema.idl.TypeDefinitionRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.config.EnableWebFlux
import java.util.function.Function

@AutoConfigureWebTestClient
@EnableWebFlux
@SpringBootTest(
    classes = [CustomPreparsedDocumentProvider.TestConfig::class, DgsWebFluxAutoConfiguration::class, DgsAutoConfiguration::class, CustomPreparsedDocumentProvider.ExampleImplementation::class],
)
class CustomPreparsedDocumentProvider {

    @Autowired
    lateinit var executor: DgsReactiveQueryExecutor

    @Test
    fun customPreparsedInject() {
        try {
            executor.execute("""{"query": "{hello}"}""", mapOf()).block()
            Assert.fail("do not inject CustomPreparsedDocumentProvider")
        } catch (e: IllegalStateException) {
        } catch (e: Exception) {
            Assert.fail("do not inject CustomPreparsedDocumentProvider")
        }
    }

    @DgsComponent
    class ExampleImplementation {

        @DgsTypeDefinitionRegistry
        fun typeDefinitionRegistry(): TypeDefinitionRegistry {
            val newRegistry = TypeDefinitionRegistry()

            val query =
                ObjectTypeDefinition
                    .newObjectTypeDefinition()
                    .name("Query")
                    .fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("hello")
                            .type(TypeName("String"))
                            .build()
                    ).build()
            newRegistry.add(query)

            return newRegistry
        }

        @DgsQuery
        fun hello(): String {
            return "Hello, DGS"
        }
    }

    @TestConfiguration
    open class TestConfig {
        @Bean
        open fun customPreparsedDocumentProvider(): PreparsedDocumentProvider {
            return CustomPreparsedDocumentProvider()
        }

        class CustomPreparsedDocumentProvider : PreparsedDocumentProvider {
            override fun getDocument(
                executionInput: ExecutionInput?,
                parseAndValidateFunction: Function<ExecutionInput, PreparsedDocumentEntry>?
            ): PreparsedDocumentEntry {
                throw CustomException("custom")
            }
        }
    }

    class CustomException(message: String?) : RuntimeException(message)
}
