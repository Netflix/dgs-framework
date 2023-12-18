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

@file:Suppress("GraphQLUnresolvedReference")

package com.netflix.graphql.dgs.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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

    private val requestExecutor = RequestExecutor { url, headers, body ->
        val httpHeaders = HttpHeaders()
        headers.forEach { httpHeaders.addAll(it.key, it.value) }

        val response = restTemplate.exchange(url, HttpMethod.POST, HttpEntity(body, httpHeaders), String::class.java)
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
            "datafoo, data.datafoo"
        ]
    )
    fun normalizeDataPath(path: String, expectedPath: String) {
        assertThat(GraphQLResponse.getDataPath(path)).isEqualTo(expectedPath)
    }

    @Test
    fun dateParse() {
        // language=json
        val jsonResponse = """
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

        server.expect(requestTo(url))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON))

        val graphQLResponse = client.executeQuery(
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
            emptyMap()
        )

        val offsetDateTime = graphQLResponse.extractValueAsObject("submitReview.edges[0].node.postedDate", OffsetDateTime::class.java)
        assertThat(offsetDateTime).isInstanceOf(OffsetDateTime::class.java)
        assertThat(offsetDateTime.dayOfMonth).isEqualTo(29)
        server.verify()
    }

    @Test
    fun populateResponseHeaders() {
        // language=json
        val jsonResponse = """
            {
              "data": {
                "submitReview": {
                   "submittedBy": "abc@netflix.com"
                }
              }
            }
        """.trimIndent()

        server.expect(requestTo(url))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON))

        val graphQLResponse = client.executeQuery(
            """query {
              submitReview(review:{movieId:1, description:""}) {
                submittedBy
              }
            }
            """.trimIndent(),
            emptyMap()
        )

        val submittedBy = graphQLResponse.extractValueAsObject("submitReview.submittedBy", String::class.java)
        assertThat(submittedBy).isEqualTo("abc@netflix.com")
        assertThat(graphQLResponse.headers["Content-Type"].orEmpty().single()).isEqualTo("application/json")
        server.verify()
    }

    @Test
    fun listAsObject() {
        // language=json
        val jsonResponse = """
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

        server.expect(requestTo(url))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON))

        val graphQLResponse = client.executeQuery(
            """mutation {
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
            emptyMap()
        )

        val listOfSubmittedBy = graphQLResponse.extractValueAsObject(
            "submitReview.edges[*].node.submittedBy",
            jsonTypeRef<List<String>>()
        )
        assertThat(listOfSubmittedBy).isInstanceOf(ArrayList::class.java)
        assertThat(listOfSubmittedBy.size).isEqualTo(2)
        server.verify()
    }

    @Test
    fun useOperationName() {
        // language=json
        val jsonResponse = """
            {
              "data": {
                "submitReview": {
                  "edges": []
                }
              }
            }
        """.trimIndent()

        server.expect(requestTo(url))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""{"operationName":"SubmitUserReview"}"""))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON))

        val graphQLResponse = client.executeQuery(
            // language=graphql
            """
            mutation SubmitUserReview {
                submitReview(review:{movieId:1, starRating:5, description:""}) {}
            }
            """.trimIndent(),
            emptyMap(),
            "SubmitUserReview"
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
        data class Response(val submitReview: Map<String, String>)
        val response = GraphQLResponse("""{"data": {"submitReview": {"submittedBy": "abc@netflix.com"}}}""")
        val result = response.dataAsObject(Response::class.java)
        assertEquals(Response(submitReview = mapOf("submittedBy" to "abc@netflix.com")), result)
    }
}
