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

package com.netflix.graphql.dgs

import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.method.DataFetchingEnvironmentArgumentResolver
import com.netflix.graphql.dgs.internal.method.InputArgumentResolver
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import graphql.ExecutionInput
import graphql.GraphQL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.Optional

class DgsDataFetchingEnvironmentIsArgumentSet {
    private val contextRunner = ApplicationContextRunner()

    @Test
    fun `DgsDataFetcherEnvironment isArgumentSet should support top level arguments, complex arguments, and explicit nulls`() {
        contextRunner.withBean(HelloFetcher::class.java).run { context ->
            val schemaProvider =
                DgsSchemaProvider(
                    applicationContext = context,
                    federationResolver = Optional.empty(),
                    existingTypeDefinitionRegistry = Optional.empty(),
                    methodDataFetcherFactory =
                        MethodDataFetcherFactory(
                            listOf(
                                DataFetchingEnvironmentArgumentResolver(context),
                                InputArgumentResolver(
                                    DefaultInputObjectMapper(),
                                ),
                            ),
                        ),
                )
            val schema =
                schemaProvider
                    .schema(
                        """
                        type Mutation {
                            inputTester(topLevelArg: String): Boolean
                            complexInputTester(personInput: PersonInput): InputTestResult
                            complexInputTesterWithDelimiter(personInput: PersonInput): InputTestResult
                        }
                        
                        type InputTestResult {
                            name: Boolean
                            city: Boolean
                        }
                        
                        input PersonInput {
                            name: String
                            address: Address
                        }
                        
                        input Address {
                            city: String
                        }
                        """.trimIndent(),
                    ).graphQLSchema
            val build = GraphQL.newGraphQL(schema).build()
            val executionInput: ExecutionInput =
                ExecutionInput
                    .newExecutionInput()
                    .query(
                        """mutation {
                        |   providedTopLevel: inputTester(topLevelArg: "test")
                        |   notProvidedTopLevel: inputTester
                        |   explicitNullTopLevel: inputTester(topLevelArg: null)
                        |   noPersonProvided: complexInputTester { name, city }
                        |   nameProvided: complexInputTester(personInput: {name: "DGS" }) { name, city }
                        |   nameAndCityProvided: complexInputTester(personInput: {name: "DGS", address: {city: "San Jose"} }) { name, city }
                        |   explicitNullCity: complexInputTester(personInput: {address: {city: null} }) { name, city }
                        |   complexInputTesterWithDelimiter(personInput: {name: "DGS", address: {city: "San Jose"} }) { name, city }
                        |}
                        |
                        """.trimMargin(),
                    ).build()
            val executionResult = build.execute(executionInput)
            assertTrue(executionResult.isDataPresent)
            val result = executionResult.getData() as Map<String, Any?>
            assertEquals(true, result["providedTopLevel"], "Explicitly provided top level argument")
            assertEquals(false, result["notProvidedTopLevel"], "Not provided top level argument")
            assertEquals(true, result["explicitNullTopLevel"], "Explicitly null value provided for top level argument")
            assertEquals(
                mapOf("name" to false, "city" to false),
                result["noPersonProvided"] as Map<*, *>,
                "Complex input type not provided at all",
            )
            assertEquals(
                mapOf("name" to true, "city" to false),
                result["nameProvided"] as Map<*, *>,
                "Complex input type with first level property provided",
            )
            assertEquals(
                mapOf("name" to true, "city" to true),
                result["nameAndCityProvided"] as Map<*, *>,
                "Complex input type with multiple levels of properties provided",
            )
            assertEquals(
                mapOf("name" to false, "city" to true),
                result["explicitNullCity"] as Map<*, *>,
                "Explicit null value for a nested property",
            )
            assertEquals(
                mapOf("name" to true, "city" to true),
                result["complexInputTesterWithDelimiter"] as Map<*, *>,
                "Paths can be expressed with . and ->",
            )
        }
    }

    @DgsComponent
    class HelloFetcher {
        /**
         * Check if a top level argument was provided explicitly.
         * An explicit null value should also return true.
         */
        @DgsMutation
        fun inputTester(
            @InputArgument topLevelArg: String?,
            dfe: DgsDataFetchingEnvironment,
        ): Boolean = dfe.isArgumentSet("topLevelArg")

        /**
         * Check properties of complex input type if they were provided explicitly.
         * An explicit null value should also return true.
         * This example uses var args for the property path (e.g. isArgumentSet("personInput", "address", "city"))
         */
        @DgsMutation
        fun complexInputTester(
            @InputArgument personInput: PersonInput?,
            dfe: DgsDataFetchingEnvironment,
        ): InputTestResult {
            val nameIsSet = dfe.isArgumentSet("personInput", "name")
            val cityIsSet = dfe.isArgumentSet("personInput", "address", "city")

            return InputTestResult(nameIsSet, cityIsSet)
        }

        /**
         * Check properties of complex input type if they were provided explicitly.
         * An explicit null value should also return true.
         * This example uses the . and -> delimiters for the property paths (e.g. isArgumentSet(personInput->address->city))
         */
        @DgsMutation
        fun complexInputTesterWithDelimiter(
            @InputArgument personInput: PersonInput?,
            dfe: DgsDataFetchingEnvironment,
        ): InputTestResult {
            val nameIsSet = dfe.isNestedArgumentSet("personInput.name")
            val cityIsSet = dfe.isNestedArgumentSet("personInput->address -> city")

            return InputTestResult(nameIsSet, cityIsSet)
        }
    }

    data class PersonInput(
        val name: String?,
        val address: Address?,
    )

    data class Address(
        val city: String?,
    )

    data class InputTestResult(
        val name: Boolean,
        val city: Boolean,
    )
}
