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

package com.netflix.graphql.dgs.mvc

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import graphql.ExecutionResultImpl
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.multipart
import org.springframework.web.multipart.MultipartFile

@WebMvcTest(DgsRestController::class)
class DgsMultipartPostControllerTest {
    @SpringBootApplication
    open class App

    @MockBean
    lateinit var dgsQueryExecutor: DgsQueryExecutor

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `Multipart form request should require a preflight header`() {
        val queryString = "mutation(\$file: Upload!) {uploadFile(file: \$file)}"

        @Language("JSON")
        val operation =
            """
            { 
                "query": "$queryString",
                "variables": { 
                    "file": null
                }
            }
            """.trimIndent()

        @Language("JSON")
        val varParameters = """{"0": ["variables.file"]}"""

        val file1 = MockMultipartFile("foo", "foo.txt", MediaType.TEXT_PLAIN_VALUE, "Hello World".toByteArray())

        `when`(
            dgsQueryExecutor.execute(
                eq(queryString),
                any(),
                any(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(
            ExecutionResultImpl.newExecutionResult().data(mapOf("Response" to "success")).build(),
        )

        mvc
            .multipart("/graphql") {
                contentType = MediaType.MULTIPART_FORM_DATA
                param("operations", operation)
                param("map", varParameters)
                file(file1)
            }.andExpect {
                status { is4xxClientError() }
            }
    }

    @Test
    fun singleFileUpload() {
        @Language("JSON")
        val operation =
            """
            { 
                "query": "mutation(${'$'}file: Upload!) {uploadFile(file: ${'$'}file)}",
                "variables": { 
                    "file": null
                }
            }
            """.trimIndent()

        @Language("JSON")
        val varParameters = """{"foo": ["variables.file"]}"""

        val queryString = "mutation(\$file: Upload!) {uploadFile(file: \$file)}"

        `when`(
            dgsQueryExecutor.execute(
                eq(queryString),
                argThat { variables -> variables["file"] is MultipartFile },
                any(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(
            ExecutionResultImpl.newExecutionResult().data(mapOf("Response" to "success")).build(),
        )

        mvc
            .multipart("/graphql") {
                contentType = MediaType.MULTIPART_FORM_DATA
                header(GraphQLCSRFRequestHeaderValidationRule.HEADER_GRAPHQL_REQUIRE_PREFLIGHT, "true")
                param("operations", operation)
                param("map", varParameters)
                file("foo", "Hello world".toByteArray())
            }.andExpect {
                status { isOk() }
                jsonPath("errors") {
                    doesNotExist()
                }
                jsonPath("data") {
                    isMap()
                }
                jsonPath("data.Response") {
                    value("success")
                }
            }
    }

    @Test
    fun multipleFileUpload() {
        @Language("JSON")
        val operation =
            """
            {
                "query": "mutation(${'$'}input: FileUploadInput!) {uploadFile(input: ${'$'}input)}",
                "variables": {
                    "input": {
                        "description": "test",
                        "files": [null, null]
                    }
                }
            }
            """.trimIndent()

        @Language("JSON")
        val varParameters = """{"0": ["variables.input.files.0"], "1": ["variables.input.files.1"]}"""

        val queryString = "mutation(\$input: FileUploadInput!) {uploadFile(input: \$input)}"

        `when`(
            dgsQueryExecutor.execute(
                eq(queryString),
                argThat { variables ->
                    val input = variables["input"] as Map<*, *>
                    val files = input["files"]
                    files is List<*> && files.size == 2 && files.all { it is MultipartFile }
                },
                any(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(
            ExecutionResultImpl.newExecutionResult().data(mapOf("Response" to "success")).build(),
        )

        mvc
            .multipart("/graphql") {
                contentType = MediaType.MULTIPART_FORM_DATA
                header(GraphQLCSRFRequestHeaderValidationRule.HEADER_GRAPHQL_REQUIRE_PREFLIGHT, "true")
                param("operations", operation)
                param("map", varParameters)
                file("0", "Hello world".toByteArray())
                file("1", "This is an example".toByteArray())
            }.andExpect {
                status { isOk() }
                jsonPath("errors") {
                    doesNotExist()
                }
                jsonPath("data") {
                    isMap()
                }
                jsonPath("data.Response") {
                    value("success")
                }
            }
    }

    @Test
    fun arrayOfFilesUpload() {
        @Language("JSON")
        val operation =
            """
            {
                "query": "mutation(${'$'}files: [Upload!]!) {uploadFile(files: ${'$'}files)}",
                "variables": {
                    "files": [null, null]
                }
            }
            """.trimIndent()

        @Language("JSON")
        val varParameters = """{"0": ["variables.files.0"], "1": ["variables.files.1"]}"""

        val queryString = "mutation(\$files: [Upload!]!) {uploadFile(files: \$files)}"

        `when`(
            dgsQueryExecutor.execute(
                eq(queryString),
                argThat { variables ->
                    val files = variables["files"]
                    files is List<*> && files.size == 2 && files.all { it is MultipartFile }
                },
                any(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(
            ExecutionResultImpl.newExecutionResult().data(mapOf("Response" to "success")).build(),
        )

        mvc
            .multipart("/graphql") {
                contentType = MediaType.MULTIPART_FORM_DATA
                header(GraphQLCSRFRequestHeaderValidationRule.HEADER_GRAPHQL_REQUIRE_PREFLIGHT, "true")
                param("operations", operation)
                param("map", varParameters)
                file("0", "Hello world".toByteArray())
                file("1", "This is an example".toByteArray())
            }.andExpect {
                status { isOk() }
                jsonPath("errors") {
                    doesNotExist()
                }
                jsonPath("data") {
                    isMap()
                }
                jsonPath("data.Response") {
                    value("success")
                }
            }
    }

    @Test
    fun incorrectFileUploadWithMissingParts() {
        // Missing operations param
        mvc
            .multipart("/graphql") {
                contentType = MediaType.MULTIPART_FORM_DATA
                header(GraphQLCSRFRequestHeaderValidationRule.HEADER_GRAPHQL_REQUIRE_PREFLIGHT, "true")
                param("map", """{"0": ["variables.file"]}""")
                file("0", "Hello world".toByteArray())
            }.andExpect {
                status { is4xxClientError() }
            }

        @Language("JSON")
        val operation =
            """
            {
                "query": "mutation(${'$'}files: [Upload!]!) {uploadFile(files: ${'$'}files)}",
                "variables": {
                    "files": [null, null]
                }
            }
            """.trimIndent()

        // Missing map param
        mvc
            .multipart("/graphql") {
                contentType = MediaType.MULTIPART_FORM_DATA
                header(GraphQLCSRFRequestHeaderValidationRule.HEADER_GRAPHQL_REQUIRE_PREFLIGHT, "true")
                param("operations", operation)
                file("0", "Hello world".toByteArray())
            }.andExpect {
                status { is4xxClientError() }
            }
    }

    @Test
    fun malformedFileUploadWithIncorrectMappedPath() {
        @Language("JSON")
        val operation =
            """
            {
                "query": "mutation(${'$'}file: Upload!) {uploadFile(file: ${'$'}file)}",
                "variables": {
                    "file": null
                }
            }
            """.trimIndent()

        // set up incorrect object mapping path
        @Language("JSON")
        val varParameters = """{"0": ["variables.file.0"]}"""

        mvc
            .multipart("/graphql") {
                contentType = MediaType.MULTIPART_FORM_DATA
                header(GraphQLCSRFRequestHeaderValidationRule.HEADER_GRAPHQL_REQUIRE_PREFLIGHT, "true")
                param("operations", operation)
                param("map", varParameters)
                file("0", "Hello world".toByteArray())
            }.andExpect {
                status { is4xxClientError() }
            }
    }
}
