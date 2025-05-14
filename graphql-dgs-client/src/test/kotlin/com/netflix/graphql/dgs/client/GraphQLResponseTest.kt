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

@file:Suppress("GraphQLUnresolvedReference")

package com.netflix.graphql.dgs.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.time.OffsetDateTime

class GraphQLResponseTest {
    private val restTemplate = RestTemplate()
    private val server = MockRestServiceServer.bindTo(restTemplate).build()

    private val requestExecutor =
        RequestExecutor { url, headers, body ->
            val httpHeaders = HttpHeaders()
            headers.forEach { httpHeaders.addAll(it.key, it.value) }

            val response =
                restTemplate.exchange(url, HttpMethod.POST, HttpEntity(body, httpHeaders), String::class.java)
            HttpResponse(statusCode = response.statusCode.value(), body = response.body, headers = response.headers)
        }

    private val url = "http://localhost:8080/graphql"
    private val client = CustomGraphQLClient(url = url, requestExecutor = requestExecutor)

    @ParameterizedTest
    @CsvSource(
        value = [
            "data, data",
            "foo, data.foo",
            "data.foo, data.foo",
            "datafoo, data.datafoo",
        ],
    )
    fun normalizeDataPath(
        path: String,
        expectedPath: String,
    ) {
        assertThat(GraphQLResponse.getDataPath(path)).isEqualTo(expectedPath)
    }

    @Test
    fun dateParse() {
        // language=json
        val jsonResponse =
            """
            {
              "data": {
                "submitReview": {
                  "edges": [
                    {
                      "node": {
                        "submittedBy": "pbakker@netflix.com",
                        "postedDate": "2020-10-29T12:22:47.789933-07:00"
                      }
                    },
                    {
                      "node": {
                        "submittedBy": "pbakker@netflix.com",
                        "postedDate": "2020-10-29T12:22:54.327407-07:00"
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()

        server
            .expect(requestTo(url))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON))

        val graphQLResponse =
            client.executeQuery(
                // language=graphql
                """
                mutation {
                  submitReview(review:{movieId:1, starRating:5, description:""}) {
                    edges {
                      node {
                        submittedBy
                        postedDate
                      }
                    }
                  }
                }
                """.trimIndent(),
                emptyMap(),
            )

        val offsetDateTime =
            graphQLResponse.extractValueAsObject("submitReview.edges[0].node.postedDate", OffsetDateTime::class.java)
        assertThat(offsetDateTime).isInstanceOf(OffsetDateTime::class.java)
        assertThat(offsetDateTime.dayOfMonth).isEqualTo(29)
        server.verify()
    }

    @Test
    fun populateResponseHeaders() {
        // language=json
        val jsonResponse =
            """
            {
              "data": {
                "submitReview": {
                   "submittedBy": "abc@netflix.com"
                }
              }
            }
            """.trimIndent()

        server
            .expect(requestTo(url))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON))

        val graphQLResponse =
            client.executeQuery(
                """
                query {
                  submitReview(review:{movieId:1, description:""}) {
                    submittedBy
                  }
                }
                """.trimIndent(),
                emptyMap(),
            )

        val submittedBy = graphQLResponse.extractValueAsObject("submitReview.submittedBy", String::class.java)
        assertThat(submittedBy).isEqualTo("abc@netflix.com")
        assertThat(graphQLResponse.headers["Content-Type"].orEmpty().single()).isEqualTo("application/json")
        server.verify()
    }

    @Test
    fun listAsObject() {
        // language=json
        val jsonResponse =
            """
            {
              "data": {
                "submitReview": {
                  "edges": [
                    {
                      "node": {
                        "submittedBy": "pbakker@netflix.com",
                        "postedDate": "2020-10-29T12:22:47.789933-07:00"
                      }
                    },
                    {
                      "node": {
                        "submittedBy": "pbakker@netflix.com",
                        "postedDate": "2020-10-29T12:22:54.327407-07:00"
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()

        server
            .expect(requestTo(url))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON))

        val graphQLResponse =
            client.executeQuery(
                """
                mutation {
                  submitReview(review:{movieId:1, starRating:5, description:""}) {
                    edges {
                      node {
                        submittedBy
                        postedDate
                      }
                    }
                  }
                }
                """.trimIndent(),
                emptyMap(),
            )

        val listOfSubmittedBy =
            graphQLResponse.extractValueAsObject(
                "submitReview.edges[*].node.submittedBy",
                jsonTypeRef<List<String>>(),
            )
        assertThat(listOfSubmittedBy).isInstanceOf(ArrayList::class.java)
        assertThat(listOfSubmittedBy.size).isEqualTo(2)
        server.verify()
    }

    @Test
    fun useOperationName() {
        // language=json
        val jsonResponse =
            """
            {
              "data": {
                "submitReview": {
                  "edges": []
                }
              }
            }
            """.trimIndent()

        server
            .expect(requestTo(url))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""{"operationName":"SubmitUserReview"}"""))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON))

        val graphQLResponse =
            client.executeQuery(
                // language=graphql
                """
                mutation SubmitUserReview {
                    submitReview(review:{movieId:1, starRating:5, description:""}) {}
                }
                """.trimIndent(),
                emptyMap(),
                "SubmitUserReview",
            )
        assertThat(graphQLResponse.hasErrors()).isFalse

        server.verify()
    }

    @Test
    fun testExtractValue() {
        val response = GraphQLResponse("""{"data": {"submitReview": {"submittedBy": "abc@netflix.com"}}}""")
        val result = response.extractValue<Map<String, Any?>>("data")
        assertEquals(mapOf("submitReview" to mapOf("submittedBy" to "abc@netflix.com")), result)
        assertEquals("abc@netflix.com", response.extractValue("data.submitReview.submittedBy"))
        assertEquals("abc@netflix.com", response.extractValue("submitReview.submittedBy"))
    }

    @Test
    fun testDataAsObject() {
        data class Response(
            val submitReview: Map<String, String>,
        )

        val response = GraphQLResponse("""{"data": {"submitReview": {"submittedBy": "abc@netflix.com"}}}""")
        val result = response.dataAsObject(Response::class.java)
        assertEquals(Response(submitReview = mapOf("submittedBy" to "abc@netflix.com")), result)
    }

    @Test
    fun testObjectErrorClassification() {
        val response =
            GraphQLResponse(
                """
                {
                    "data":  null,
                    "errors": [{
                      "message": "Validation failed",
                      "path": ["shows", 12],
                      "extensions": {
                        "errorType": "INTERNAL",
                        "classification": {
                          "type": "ExtendedValidationError",
                          "validatedPath": ["shows", "title"]
                        }
                      }
                    }]
                }
                """.trimIndent(),
            )
        val error = response.errors.singleOrNull() ?: fail("Expected single error on response: $response")
        assertThat(error.message).isEqualTo("Validation failed")
        assertThat(error.path).isEqualTo(listOf("shows", 12))

        val extensions = error.extensions ?: fail("Expected non-null extensions on error: $error")
        assertThat(extensions.errorType).isEqualTo(ErrorType.INTERNAL)
        assertThat(extensions.classification).isEqualTo(
            mapOf(
                "type" to "ExtendedValidationError",
                "validatedPath" to listOf("shows", "title"),
            ),
        )
    }

    @Test
    fun testExplicitNullPath() {
        val response =
            GraphQLResponse(
                """
                {
                    "data": null,
                    "errors": [{
                      "message": "Validation failed",
                      "path": null,
                      "extensions": {
                        "errorType": "INTERNAL",
                        "classification": {
                          "type": "ExtendedValidationError",
                          "validatedPath": ["shows", "title"]
                        }
                      }
                    }]
                }
                """.trimIndent(),
            )
        assertThat(response.data).isEmpty()
        assertThat(response.errors).hasSize(1)
        assertThat(response.errors[0].path).isEmpty()
        assertThat(response.errors[0].message).isEqualTo("Validation failed")
    }

    @Test
    fun testMinimalResponse() {
        val response = GraphQLResponse("""{"errors": [{"message": "An error occurred"}]}""")
        assertThat(response.errors).hasSize(1)
        assertThat(response.errors[0].message).isEqualTo("An error occurred")
        assertThat(response.errors[0].path).isEmpty()
    }

    @Test
    fun testResponseWithExtraData() {
        data class Response(
            val foo: String,
        )

        val response = GraphQLResponse("""{"data": {"foo": "bar", "extra": "baz"}}""")
        assertThrows<IllegalArgumentException> { response.dataAsObject(Response::class.java) }
    }

    @Test
    fun testResponseWithExtraDataCustomObjectMapper() {
        val objectMapper =
            ObjectMapper()
                .findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

        data class Response(
            val foo: String,
        )

        val response = GraphQLResponse("""{"data": {"foo": "bar", "extra": "baz"}}""", objectMapper)
        val deserialized = assertDoesNotThrow { response.dataAsObject(Response::class.java) }
        assertThat(deserialized).isEqualTo(Response("bar"))
    }
}
