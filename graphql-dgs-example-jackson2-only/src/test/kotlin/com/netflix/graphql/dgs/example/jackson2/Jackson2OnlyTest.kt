package com.netflix.graphql.dgs.example.jackson2

import com.netflix.graphql.dgs.client.CustomGraphQLClient
import com.netflix.graphql.dgs.client.DgsGraphQLResponse
import com.netflix.graphql.dgs.client.RestClientGraphQLClient
import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class Jackson2OnlyTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @LocalServerPort
    var port: Int = 0

    @Test
    fun `Jackson 3 is NOT on the classpath`() {
        val result =
            runCatching {
                Class.forName("tools.jackson.databind.json.JsonMapper")
            }
        assertThat(result.isFailure)
            .withFailMessage("Expected Jackson 3 JsonMapper to NOT be on classpath, but it was found")
            .isTrue()
    }

    @Test
    fun `Jackson 2 IS on the classpath`() {
        val clazz = Class.forName("com.fasterxml.jackson.databind.ObjectMapper")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun `GraphQL query works with Jackson 2 only`() {
        mockMvc
            .post("/graphql") {
                content = """{"query":"{ hello(name: \"Jackson2\") }"}"""
                contentType = MediaType.APPLICATION_JSON
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.hello") { value("hello, Jackson2!") }
            }
    }

    @Suppress("DEPRECATION")
    @Test
    fun `RestClientGraphQLClient works with Jackson 2`() {
        val restClient = RestClient.builder().baseUrl("http://localhost:$port/graphql").build()
        val client = RestClientGraphQLClient(restClient)

        val response = client.executeQuery("{ hello(name: \"RestClient\") }")

        assertThat(response).isInstanceOf(DgsGraphQLResponse::class.java)
        assertThat(response.hasErrors()).isFalse()
        assertThat(response.extractValue<String>("hello")).isEqualTo("hello, RestClient!")
    }

    @Suppress("DEPRECATION")
    @Test
    fun `WebClientGraphQLClient works with Jackson 2`() {
        val webClient = WebClient.create("http://localhost:$port/graphql")
        val client = WebClientGraphQLClient(webClient)

        val response = client.reactiveExecuteQuery("{ hello(name: \"WebClient\") }").block()!!

        assertThat(response).isInstanceOf(DgsGraphQLResponse::class.java)
        assertThat(response.hasErrors()).isFalse()
        assertThat(response.extractValue<String>("hello")).isEqualTo("hello, WebClient!")
    }

    @Suppress("DEPRECATION")
    @Test
    fun `CustomGraphQLClient works with Jackson 2`() {
        val client =
            CustomGraphQLClient("http://localhost:$port/graphql") { url, headers, body ->
                val restClient = RestClient.builder().baseUrl(url).build()
                val response =
                    restClient
                        .post()
                        .headers { h ->
                            headers.forEach { (key, values) -> h.addAll(key, values) }
                        }.body(body)
                        .retrieve()
                        .toEntity(String::class.java)
                com.netflix.graphql.dgs.client.HttpResponse(
                    response.statusCode.value(),
                    response.body,
                    response.headers.toMap(),
                )
            }

        val response = client.executeQuery("{ hello(name: \"Custom\") }")

        assertThat(response).isInstanceOf(DgsGraphQLResponse::class.java)
        assertThat(response.hasErrors()).isFalse()
        assertThat(response.extractValue<String>("hello")).isEqualTo("hello, Custom!")
    }
}

private fun HttpHeaders.toMap(): Map<String, List<String>> {
    val result = mutableMapOf<String, List<String>>()
    this.forEach { key, values -> result[key] = values }
    return result
}
