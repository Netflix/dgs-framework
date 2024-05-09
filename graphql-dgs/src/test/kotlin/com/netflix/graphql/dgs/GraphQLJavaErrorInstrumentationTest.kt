/*
 * Copyright 2024 Netflix, Inc.
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

import com.netflix.graphql.dgs.internal.GraphQLJavaErrorInstrumentation
import graphql.GraphQL
import graphql.execution.instrumentation.Instrumentation
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeRuntimeWiring
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GraphQLJavaErrorInstrumentationTest {

    private lateinit var graphqlJavaErrorInstrumentation: Instrumentation

    private val schema = """
                type Query{
                    hello(name: String!): String
                }
    """.trimMargin()

    @BeforeEach
    fun setup() {
        graphqlJavaErrorInstrumentation = GraphQLJavaErrorInstrumentation()
    }

    @Test
    fun `Validation errors are not added for valid queries`() {
        val graphQL: GraphQL = buildGraphQL(schema)
        val result = graphQL.execute(
            """
            {
                hello(name: "xyz")
            }
            """.trimIndent()
        )

        Assertions.assertThat(result.isDataPresent).isTrue
        val data = result.getData<Map<String, String>>()
        Assertions.assertThat(data["hello"]).isEqualTo("hello there!")
    }

    @Test
    fun `Validation errors contain errorDetail and errorType in the extensions for invalid fields`() {
        val graphQL: GraphQL = buildGraphQL(schema)
        val result = graphQL.execute(
            """
            {
                InvalidField
            }
            """.trimIndent()
        )

        Assertions.assertThat(result.isDataPresent).isFalse
        Assertions.assertThat(result.errors.size).isEqualTo(1)
        Assertions.assertThat(result.errors[0].extensions.keys.containsAll(listOf("classification", "errorDetail", "errorType")))
        Assertions.assertThat(result.errors[0].extensions["classification"]).isEqualTo("ValidationError")
        Assertions.assertThat(result.errors[0].extensions["errorType"]).isEqualTo("BAD_REQUEST")
        Assertions.assertThat(result.errors[0].extensions["errorDetail"]).isEqualTo("FIELD_NOT_FOUND")
    }

    @Test
    fun `Validation errors contain errorDetail and errorType in the extensions for invalid input`() {
        val graphQL: GraphQL = buildGraphQL(schema)
        val result = graphQL.execute(
            """
            {
                hello
            }
            """.trimIndent()
        )

        Assertions.assertThat(result.isDataPresent).isFalse
        Assertions.assertThat(result.errors.size).isEqualTo(1)
        Assertions.assertThat(result.errors[0].extensions.keys.containsAll(listOf("classification", "errorType")))
        Assertions.assertThat(result.errors[0].extensions["classification"]).isEqualTo("ValidationError")
        Assertions.assertThat(result.errors[0].extensions["errorType"]).isEqualTo("BAD_REQUEST")
    }

    @Test
    fun `Validation errors contain errorDetail and errorType in the extensions for invalid syntax`() {
        val graphQL: GraphQL = buildGraphQL(schema)
        val result = graphQL.execute(
            """
            {
                hello(
            }
            """.trimIndent()
        )

        Assertions.assertThat(result.isDataPresent).isFalse
        Assertions.assertThat(result.errors.size).isEqualTo(1)
        Assertions.assertThat(result.errors[0].extensions.keys.containsAll(listOf("classification", "errorDetail", "errorType")))
        Assertions.assertThat(result.errors[0].extensions["classification"]).isEqualTo("InvalidSyntax")
        Assertions.assertThat(result.errors[0].extensions["errorType"]).isEqualTo("BAD_REQUEST")
    }

    @Test
    fun `Multiple validation errors contain errorDetail and errorType in the extensions for multiple invalid fields`() {
        val graphQL: GraphQL = buildGraphQL(schema)
        val result = graphQL.execute(
            """
            {
                InvalidField
                helloInvalid
            }
            """.trimIndent()
        )

        Assertions.assertThat(result.isDataPresent).isFalse
        Assertions.assertThat(result.errors.size).isEqualTo(2)
        Assertions.assertThat(result.errors[0].extensions.keys.containsAll(listOf("classification", "errorDetail", "errorType")))
        Assertions.assertThat(result.errors[0].extensions["classification"]).isEqualTo("ValidationError")
        Assertions.assertThat(result.errors[0].extensions["errorType"]).isEqualTo("BAD_REQUEST")
        Assertions.assertThat(result.errors[0].extensions["errorDetail"]).isEqualTo("FIELD_NOT_FOUND")
        Assertions.assertThat(result.errors[1].extensions.keys.containsAll(listOf("class", "errorDetail", "errorType")))
        Assertions.assertThat(result.errors[1].extensions["classification"]).isEqualTo("ValidationError")
        Assertions.assertThat(result.errors[1].extensions["errorType"]).isEqualTo("BAD_REQUEST")
        Assertions.assertThat(result.errors[1].extensions["errorDetail"]).isEqualTo("FIELD_NOT_FOUND")
    }

    private fun buildGraphQL(schema: String): GraphQL {
        val schemaParser = SchemaParser()
        val typeDefinitionRegistry = schemaParser.parse(schema)
        val runtimeWiring = RuntimeWiring.newRuntimeWiring()
            .type("Query") { builder: TypeRuntimeWiring.Builder ->
                builder.dataFetcher("hello", StaticDataFetcher("hello there!"))
            }
            .build()
        val schemaGenerator = SchemaGenerator()
        val graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)

        return GraphQL.newGraphQL(graphQLSchema).instrumentation(graphqlJavaErrorInstrumentation).build()
    }
}
