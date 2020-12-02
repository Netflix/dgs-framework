package com.netflix.graphql.dgs.client

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

class ErrorsTest {

    private val restTemplate = RestTemplate()
    private val server = MockRestServiceServer.bindTo(restTemplate).build()

    private val requestExecutor = RequestExecutor { url, headers, body ->
        val httpHeaders = HttpHeaders()
        headers.forEach { httpHeaders.addAll(it.key, it.value) }

        val exchange = restTemplate.exchange(url, HttpMethod.POST, HttpEntity(body, httpHeaders), String::class.java)
        HttpResponse(exchange.statusCodeValue, exchange.body)
    }

    private val url = "http://localhost:8080/graphql"
    private val client = DefaultGraphQLClient(url)

    @Test
    fun unknownErrorType() {

        val jsonResponse = """
            {
              "errors": [
                {
                  "message": "java.lang.RuntimeException: test",
                  "locations": [],
                  "path": [
                    "hello"
                  ],
                  "extensions": {
                    "errorType": "badtype"
                  }
                }
              ],
              "data": {
                "hello": null
              }
            }
        """.trimIndent()

        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON))

        val graphQLResponse = client.executeQuery("""{ hello }""", emptyMap(), requestExecutor)
        assertThat(graphQLResponse.errors[0].extensions.errorType).isEqualTo(ErrorType.UNKNOWN)

        server.verify()
    }

    @Test
    fun errorType() {
        val jsonResponse = """
            {
              "errors": [
                {
                  "message": "java.lang.RuntimeException: test",
                  "locations": [],
                  "path": [
                    "hello"
                  ],
                  "extensions": {
                    "errorType": "BAD_REQUEST"
                  }
                }
              ],
              "data": {
                "hello": null
              }
            }
        """.trimIndent()

        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON))

        val graphQLResponse = client.executeQuery("""{ hello }""", emptyMap(), requestExecutor)
        assertThat(graphQLResponse.errors[0].extensions.errorType).isEqualTo(ErrorType.BAD_REQUEST)

        server.verify()
    }

    @Test
    fun errorDetails() {
        val jsonResponse = """
            {
              "errors": [
                {
                  "message": "java.lang.RuntimeException: test",
                  "locations": [],
                  "path": [
                    "hello"
                  ],
                  "extensions": {
                    "errorType": "BAD_REQUEST",
                    "errorDetail": "FIELD_NOT_FOUND"
                  }
                }
              ],
              "data": {
                "hello": null
              }
            }
        """.trimIndent()

        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON))

        val graphQLResponse = client.executeQuery("""{ hello }""", emptyMap(), requestExecutor)
        assertThat(graphQLResponse.errors[0].extensions.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(graphQLResponse.errors[0].extensions.errorDetail).isEqualTo("FIELD_NOT_FOUND")

        server.verify()
    }
}