package com.netflix.graphql.dgs.example.jacksonboth

import com.netflix.graphql.dgs.client.GraphQLClientResponse
import com.netflix.graphql.dgs.client.Jackson3RestClientGraphQLClient
import com.netflix.graphql.dgs.client.RestClientGraphQLClient
import com.netflix.graphql.dgs.internal.DgsJsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.web.client.RestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class JacksonBothTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var dgsJsonMapper: DgsJsonMapper

    @LocalServerPort
    var port: Int = 0

    @Test
    fun `both Jackson 2 and Jackson 3 are on the classpath`() {
        val jackson2 = Class.forName("com.fasterxml.jackson.databind.ObjectMapper")
        val jackson3 = Class.forName("tools.jackson.databind.json.JsonMapper")
        assertThat(jackson2).isNotNull()
        assertThat(jackson3).isNotNull()
    }

    @Test
    fun `Jackson 2 wins autoconfiguration when jackson2 module is present`() {
        assertThat(dgsJsonMapper.javaClass.simpleName).isEqualTo("Jackson2DgsJsonMapper")
    }

    @Test
    fun `GraphQL endpoint works`() {
        mockMvc
            .post("/graphql") {
                content = """{"query":"{ hello(name: \"Both\") }"}"""
                contentType = MediaType.APPLICATION_JSON
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.hello") { value("hello, Both!") }
            }
    }

    @Suppress("DEPRECATION")
    @Test
    fun `Jackson 2 client classes work`() {
        val restClient = RestClient.builder().baseUrl("http://localhost:$port/graphql").build()
        val client = RestClientGraphQLClient(restClient)

        val response = client.executeQuery("{ hello(name: \"Jackson2Client\") }")

        assertThat(response).isInstanceOf(GraphQLClientResponse::class.java)
        assertThat(response.hasErrors()).isFalse()
        assertThat(response.extractValue<String>("hello")).isEqualTo("hello, Jackson2Client!")
    }

    @Test
    fun `Jackson 3 client classes work`() {
        val restClient = RestClient.builder().baseUrl("http://localhost:$port/graphql").build()
        val client = Jackson3RestClientGraphQLClient(restClient)

        val response = client.executeQuery("{ hello(name: \"Jackson3Client\") }")

        assertThat(response).isInstanceOf(GraphQLClientResponse::class.java)
        assertThat(response.hasErrors()).isFalse()
        assertThat(response.extractValue<String>("hello")).isEqualTo("hello, Jackson3Client!")
    }
}
