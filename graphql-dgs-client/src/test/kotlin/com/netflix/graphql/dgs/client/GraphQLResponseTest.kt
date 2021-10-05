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

package com.netflix.graphql.dgs.client

import com.jayway.jsonpath.TypeRef
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
import java.time.format.DateTimeFormatter

@Suppress("DEPRECATION")
class GraphQLResponseTest {

    private val restTemplate = RestTemplate()
    private val server = MockRestServiceServer.bindTo(restTemplate).build()

    private val requestExecutor = RequestExecutor { url, headers, body ->
        val httpHeaders = HttpHeaders()
        headers.forEach { httpHeaders.addAll(it.key, it.value) }

        val exchange = restTemplate.exchange(url, HttpMethod.POST, HttpEntity(body, httpHeaders), String::class.java)
        HttpResponse(exchange.statusCodeValue, exchange.body)
    }

    private val requestExecutorWithResponseHeaders = RequestExecutor { url, headers, body ->
        val httpHeaders = HttpHeaders()
        headers.forEach { httpHeaders.addAll(it.key, it.value) }

        val exchange = restTemplate.exchange(url, HttpMethod.POST, HttpEntity(body, httpHeaders), String::class.java)
        HttpResponse(exchange.statusCodeValue, exchange.body, exchange.headers.toMap())
    }

    private val url = "http://localhost:8080/graphql"
    private val client = DefaultGraphQLClient(
        url,
        mapOf(
            OffsetDateTime::class.java to { t -> OffsetDateTime.parse(t.toString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME) },
            Person::class.java to { t -> Person("Custom Person", 20) }
        )
    )

    @Test
    fun dateParse() {

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
            }""",
            emptyMap(), requestExecutor
        )

        val offsetDateTime = graphQLResponse.extractValueAsObject("submitReview.edges[0].node.postedDate", OffsetDateTime::class.java)
        assertThat(offsetDateTime).isInstanceOf(OffsetDateTime::class.java)
        assertThat(offsetDateTime.dayOfMonth).isEqualTo(29)
        server.verify()
    }

    @Test
    fun populateResponseHeaders() {
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
            }""",
            emptyMap(), requestExecutorWithResponseHeaders
        )

        val submittedBy = graphQLResponse.extractValueAsObject("submitReview.submittedBy", String::class.java)
        assertThat(submittedBy).isEqualTo("abc@netflix.com")
        assertThat(graphQLResponse.headers["Content-Type"]?.get(0)).isEqualTo("application/json")
        server.verify()
    }

    @Test
    fun listAsObject() {

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
            }""",
            emptyMap(), requestExecutor
        )

        val listOfSubmittedBy: List<String> = graphQLResponse.extractValueAsObject(
            "submitReview.edges[*].node.submittedBy",
            object : TypeRef<List<String>>() {}
        )
        assertThat(listOfSubmittedBy).isInstanceOf(ArrayList::class.java)
        assertThat(listOfSubmittedBy.size).isEqualTo(2)
        server.verify()
    }

    @Test
    fun dateParseWithNestedDateDeserialization() {

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
            }""",
            emptyMap(), requestExecutor
        )

        data class Node(val submittedBy: String, val postedDate: OffsetDateTime)

        val node: Node = graphQLResponse.extractValueAsObject("submitReview.edges[0].node", Node::class.java)
        assertThat(node.postedDate).isInstanceOf(OffsetDateTime::class.java)
        assertThat(node.postedDate.dayOfMonth).isEqualTo(29)
        server.verify()
    }

    @Test
    fun useOperationName() {

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
            """mutation SubmitUserReview {
              submitReview(review:{movieId:1, starRating:5, description:""}) {}
            }""",
            emptyMap(), "SubmitUserReview", requestExecutor
        )

        server.verify()
    }

    @Test
    fun customPersonParse() {

        val jsonResponse = """
            {
              "data": {
                "submitReview": {
                  "edges": [
                    {
                      "node": {
                        "name": "PersonA",
                        "age": 10
                      }
                    },
                    {
                      "node": {
                        "name": "PersonB",
                        "age": "30"
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
                    name
                    age
                  }
                }
              }
            }""",
            emptyMap(), requestExecutor
        )

        val person = graphQLResponse.extractValueAsObject("submitReview.edges[0].node", Person::class.java)
        assertThat(person).isInstanceOf(Person::class.java)
        assertThat(person.age).isEqualTo(20)
        assertThat(person.name).isEqualTo("Custom Person")
        server.verify()
    }
}

data class Person(val name: String, val age: Int)
