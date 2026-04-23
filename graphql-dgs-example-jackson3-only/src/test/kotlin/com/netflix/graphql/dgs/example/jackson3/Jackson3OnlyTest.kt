package com.netflix.graphql.dgs.example.jackson3

import com.netflix.graphql.dgs.client.DgsCustomGraphQLClient
import com.netflix.graphql.dgs.client.DgsGraphQLResponse
import com.netflix.graphql.dgs.client.DgsRestClientGraphQLClient
import com.netflix.graphql.dgs.client.DgsWebClientGraphQLClient
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
class Jackson3OnlyTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @LocalServerPort
    var port: Int = 0

    @Test
    fun `Jackson 2 runtime is NOT on the classpath`() {
        val result =
            runCatching {
                Class.forName("com.fasterxml.jackson.databind.ObjectMapper")
            }
        assertThat(result.isFailure)
            .withFailMessage("Expected Jackson 2 ObjectMapper to NOT be on classpath, but it was found")
            .isTrue()
    }

    @Test
    fun `Jackson 3 IS on the classpath`() {
        val clazz = Class.forName("tools.jackson.databind.json.JsonMapper")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun `GraphQL query works with Jackson 3 only`() {
        mockMvc
            .post("/graphql") {
                content = """{"query":"{ hello(name: \"Jackson3\") }"}"""
                contentType = MediaType.APPLICATION_JSON
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.hello") { value("hello, Jackson3!") }
            }
    }

    @Test
    fun `DgsRestClientGraphQLClient works`() {
        val restClient = RestClient.builder().baseUrl("http://localhost:$port/graphql").build()
        val client = DgsRestClientGraphQLClient(restClient)

        val response = client.executeQuery("{ hello(name: \"RestClient\") }")

        assertThat(response).isInstanceOf(DgsGraphQLResponse::class.java)
        assertThat(response.hasErrors()).isFalse()
        assertThat(response.extractValue<String>("hello")).isEqualTo("hello, RestClient!")
    }

    @Test
    fun `DgsWebClientGraphQLClient works`() {
        val webClient = WebClient.create("http://localhost:$port/graphql")
        val client = DgsWebClientGraphQLClient(webClient)

        val response = client.reactiveExecuteQuery("{ hello(name: \"WebClient\") }").block()!!

        assertThat(response).isInstanceOf(DgsGraphQLResponse::class.java)
        assertThat(response.hasErrors()).isFalse()
        assertThat(response.extractValue<String>("hello")).isEqualTo("hello, WebClient!")
    }

    @Test
    fun `DgsCustomGraphQLClient works`() {
        val client =
            DgsCustomGraphQLClient("http://localhost:$port/graphql") { url, headers, body ->
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

    @Test
    fun `Jackson 2 client classes fail with NoClassDefFoundError`() {
        val result =
            runCatching {
                Class
                    .forName("com.netflix.graphql.dgs.client.RestClientGraphQLClient")
                    .getDeclaredConstructor(RestClient::class.java)
                    .newInstance(RestClient.builder().baseUrl("http://localhost:$port/graphql").build())
            }
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(NoClassDefFoundError::class.java)
    }
}

private fun HttpHeaders.toMap(): Map<String, List<String>> {
    val result = mutableMapOf<String, List<String>>()
    this.forEach { key, values -> result[key] = values }
    return result
}
