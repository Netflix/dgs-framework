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
import java.util.*

@SpringBootTest(classes = [BeanValidationSizeSmokeTest.LocalApp::class])
@EnableAutoConfiguration
internal class BeanValidationSizeSmokeTest {

    @Autowired
    lateinit var queryExecutor: DgsQueryExecutor

    @Test
    fun createPostWithInvalidInput() {
        val query = "mutation newPost(\$input: CreatePostInput!){ createPost(createPostInput: \$input) }"
        var variables = mapOf(
            "input" to mapOf(
                "title" to "test", // conflict with @Size directive
                "content" to "test content"
            )
        )
        val executionResult = queryExecutor.execute(query, variables)
        assertThat(executionResult.errors).isNotEmpty()
        assertThat(executionResult.errors[0].message).isEqualTo("/createPost/createPostInput/title size must be between 5 and 50")
        assertThat(executionResult.getData<Map<String, Any>>()).isNull()
    }

    @Test
    fun createPostWithValidInput() {
        val query = "mutation newPost(\$input: CreatePostInput!){ createPost(createPostInput: \$input) }"
        var variables = mapOf(
            "input" to mapOf(
                "title" to "test title",
                "content" to "test content"
            )
        )
        val executionResult = queryExecutor.execute(query, variables)
        assertThat(executionResult.errors).isEmpty()
        assertThat(executionResult.getData<Map<String, Any>>()).isNotNull()
        assertThat(executionResult.getData<Map<String, Any>>()["createPost"]).isNotNull()
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
                | type Mutation {
                |    createPost(createPostInput: CreatePostInput!): String!
                | }
                | 
                | input CreatePostInput {
                |     title: String! @Size(min:5, max:50)
                |     content: String!
                | }
                | 
                | directive @Size(min : Int = 0, max : Int = 2147483647, message : String = "graphql.validation.Size.message")
                | on ARGUMENT_DEFINITION | INPUT_FIELD_DEFINITION
                """.trimMargin()
                return schemaParser.parse(gqlSchema)
            }

            @DgsMutation
            fun createPost(@InputArgument createPostInput: CreatePostInput): String {
                println(createPostInput)
                return UUID.randomUUID().toString()
            }
        }

        data class CreatePostInput(val title: String, val content: String)
    }
}
