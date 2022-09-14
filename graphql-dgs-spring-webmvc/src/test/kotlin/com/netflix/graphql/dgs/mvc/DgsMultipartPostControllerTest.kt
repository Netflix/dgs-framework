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

import com.netflix.graphql.dgs.DgsQueryExecutor
import graphql.ExecutionResultImpl
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.InstanceOfAssertFactories.map
import org.assertj.core.util.Lists
import org.assertj.core.util.Maps
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.context.request.WebRequest
import org.springframework.web.multipart.MultipartFile

@ExtendWith(MockKExtension::class)
class DgsMultipartPostControllerTest {

    private val httpHeaders = HttpHeaders().apply {
        contentType = MediaType.MULTIPART_FORM_DATA
        add(GraphQLCSRFRequestHeaderValidationRule.HEADER_GRAPHQL_REQUIRE_PREFLIGHT, null)
    }

    @MockK
    lateinit var dgsQueryExecutor: DgsQueryExecutor

    @MockK
    lateinit var webRequest: WebRequest

    @Test
    fun `Multipart form request should require a preflight header`() {
        @Language("JSON")
        val operation = """
            { 
                "query": "mutation(${'$'}file: Upload!) {uploadFile(file: ${'$'}file)}",
                "variables": { 
                    "file": null
                }
            }
        """.trimIndent()

        @Language("JSON")
        val varParameters = """ { "0": ["variables.file"] } """.trimIndent()

        val file1: MultipartFile = MockMultipartFile("foo", "foo.txt", MediaType.TEXT_PLAIN_VALUE, "Hello World".toByteArray())

        val queryString = "mutation(${'$'}file: Upload!) {uploadFile(file: ${'$'}file)}"
        val variablesMap: MutableMap<String, Any> = Maps.newHashMap("file", file1)

        every { dgsQueryExecutor.execute(queryString, variablesMap, any(), any(), any(), any()) } returns(
            ExecutionResultImpl.newExecutionResult().data(mapOf("Response" to "success")).build()
            )
        // HTTP Headers with only the expected content-type but no preflight
        val noPreflightHeader = HttpHeaders()
        noPreflightHeader.contentType = MediaType.MULTIPART_FORM_DATA

        val result = DgsRestController(dgsQueryExecutor).graphql(
            body = null,
            fileParams = Maps.newHashMap("0", file1),
            operation = operation,
            mapParam = varParameters,
            noPreflightHeader,
            webRequest
        )

        assertThat(result.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(result.body).isInstanceOfSatisfying(String::class.java) { body ->
            assertThat(body).isNotEmpty()
            assertThat(body).contains("Expecting a CSRF Prevention Header but none was found, supported headers are [apollo-require-preflight, x-apollo-operation-name, graphql-require-preflight].")
        }
    }

    @Test
    fun singleFileUpload() {
        @Language("JSON")
        val operation = """
            { 
                "query": "mutation(${'$'}file: Upload!) {uploadFile(file: ${'$'}file)}",
                "variables": { 
                    "file": null
                }
            }
        """.trimIndent()

        @Language("JSON")
        val varParameters = """
            { 
                "0": ["variables.file"]
            }
        """.trimIndent()

        val file1: MultipartFile = MockMultipartFile("foo", "foo.txt", MediaType.TEXT_PLAIN_VALUE, "Hello World".toByteArray())

        val queryString = "mutation(${'$'}file: Upload!) {uploadFile(file: ${'$'}file)}"
        val variablesMap: MutableMap<String, Any> = Maps.newHashMap("file", file1)

        every { dgsQueryExecutor.execute(queryString, variablesMap, any(), any(), any(), any()) } returns(
            ExecutionResultImpl.newExecutionResult().data(mapOf("Response" to "success")).build()
            )

        val result = DgsRestController(dgsQueryExecutor).graphql(
            body = null,
            fileParams = Maps.newHashMap("0", file1),
            operation = operation,
            mapParam = varParameters,
            httpHeaders,
            webRequest
        )

        assertThat(result.body).asInstanceOf(map(String::class.java, Any::class.java))
            .doesNotContainKey("errors")
            .extracting("data").asInstanceOf(map(String::class.java, Any::class.java))
            .extracting("Response").isEqualTo("success")
    }

    @Test
    fun multipleFileUpload() {
        @Language("JSON")
        val operation = """
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
        val varParameters = """
            { 
                "0": ["variables.input.files.0"], 
                "1": ["variables.input.files.1"] 
            }
        """.trimIndent()

        val file1: MultipartFile = MockMultipartFile("foo", "foo.txt", MediaType.TEXT_PLAIN_VALUE, "Hello World".toByteArray())
        val file2: MultipartFile = MockMultipartFile("example", "example.txt", MediaType.TEXT_PLAIN_VALUE, "This is an example".toByteArray())

        val queryString = "mutation(${'$'}input: FileUploadInput!) {uploadFile(input: ${'$'}input)}"
        val queryInputMap = Maps.newHashMap<String, Any>("description", "test")
        queryInputMap["files"] = Lists.newArrayList(file1, file2)

        every { dgsQueryExecutor.execute(queryString, mapOf("input" to queryInputMap), any(), any(), any(), any()) } returns(
            ExecutionResultImpl.newExecutionResult().data(mapOf("Response" to "success")).build()
            )

        val result = DgsRestController(dgsQueryExecutor).graphql(
            body = null,
            fileParams = mapOf("0" to file1, "1" to file2),
            operation = operation,
            mapParam = varParameters,
            httpHeaders,
            webRequest
        )

        assertThat(result.body).asInstanceOf(map(String::class.java, Any::class.java))
            .doesNotContainKey("errors")
            .extracting("data").asInstanceOf(map(String::class.java, Any::class.java))
            .extracting("Response").isEqualTo("success")
    }

    @Test
    fun arrayOfFilesUpload() {
        @Language("JSON")
        val operation = """
            { 
                "query": "mutation(${'$'}files: [Upload!]!) {uploadFile(files: ${'$'}files)}",
                "variables": { 
                    "files": [null, null]
                }
            }
        """.trimIndent()

        @Language("JSON")
        val varParameters = """
            { 
                "0": ["variables.files.0"], 
                "1": ["variables.files.1"]
            }
        """.trimIndent()

        val file1: MultipartFile = MockMultipartFile("foo", "foo.txt", MediaType.TEXT_PLAIN_VALUE, "Hello World".toByteArray())
        val file2: MultipartFile = MockMultipartFile("example", "example.txt", MediaType.TEXT_PLAIN_VALUE, "This is an example".toByteArray())

        val queryString = "mutation(${'$'}files: [Upload!]!) {uploadFile(files: ${'$'}files)}"
        val variablesMap: MutableMap<String, Any> = Maps.newHashMap("files", Lists.newArrayList(file1, file2))

        every { dgsQueryExecutor.execute(queryString, variablesMap, any(), any(), any(), any()) } returns(
            ExecutionResultImpl.newExecutionResult().data(mapOf(Pair("Response", "success"))).build()
            )

        val result = DgsRestController(dgsQueryExecutor).graphql(
            body = null,
            fileParams = mapOf("0" to file1, "1" to file2),
            operation = operation,
            mapParam = varParameters,
            httpHeaders,
            webRequest
        )

        assertThat(result.body).asInstanceOf(map(String::class.java, Any::class.java))
            .doesNotContainKey("errors")
            .extracting("data").asInstanceOf(map(String::class.java, Any::class.java))
            .extracting("Response").isEqualTo("success")
    }

    @Test
    fun incorrectFileUploadWithMissingParts() {
        @Language("JSON")
        val operation = """
            { 
                "query": "mutation(${'$'}file: Upload!) {uploadFile(file: ${'$'}file)}",
                "variables": { 
                    "file": null
                }
            }
        """.trimIndent()

        @Language("JSON")
        val varParameters = """
            { 
                "0": ["variables.file"]
            }
        """.trimIndent()

        val file: MultipartFile = MockMultipartFile("foo", "foo.txt", MediaType.TEXT_PLAIN_VALUE, "Hello World".toByteArray())

        // missing operation part
        var responseEntity = DgsRestController(dgsQueryExecutor).graphql(
            body = null,
            fileParams = mapOf("0" to file),
            operation = null,
            mapParam = varParameters,
            httpHeaders,
            webRequest
        )

        assertThat(responseEntity.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)

        // missing file parts
        responseEntity = DgsRestController(dgsQueryExecutor).graphql(null, null, operation, varParameters, httpHeaders, webRequest)
        assertThat(responseEntity.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)

        // missing mapped object paths part
        responseEntity = DgsRestController(dgsQueryExecutor).graphql(null, mapOf("0" to file), operation, null, httpHeaders, webRequest)
        assertThat(responseEntity.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun malformedFileUploadWithIncorrectMappedPath() {
        @Language("JSON")
        val operation = """
            { 
                "query": "mutation(${'$'}file: Upload!) {uploadFile(file: ${'$'}file)}",
                "variables": { 
                    "file": null
                }
            }
        """.trimIndent()

        // set up incorrect object mapping path
        @Language("JSON")
        val varParameters = """
            { 
                "0": ["variables.file.0"]
            }
        """.trimIndent()

        val file: MultipartFile = MockMultipartFile("foo", "foo.txt", MediaType.TEXT_PLAIN_VALUE, "Hello World".toByteArray())

        assertThrows(RuntimeException::class.java) {
            DgsRestController(dgsQueryExecutor).graphql(
                body = null,
                fileParams = mapOf("0" to file),
                operation = operation,
                mapParam = varParameters,
                httpHeaders,
                webRequest
            )
        }
    }
}
