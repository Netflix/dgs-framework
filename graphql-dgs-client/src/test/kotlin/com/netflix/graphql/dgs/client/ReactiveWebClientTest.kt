package com.netflix.graphql.dgs.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class ReactiveWebClientTest {
    private val url = "http://test"

    private val requestExecutor = MonoRequestExecutor { url, headers, body ->
        WebClient.builder()
            .exchangeFunction {
                Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("content-type", "application/json")
                    .body("""{ "data": { "hello": "Hi!"}}""")
                    .build())
            }.build()
            .post()
            .uri(url)
            .headers { consumer -> headers.forEach { consumer.addAll(it.key, it.value) } }
            .bodyValue(body)
            .exchange()
            .map { HttpResponse(it.rawStatusCode(), it.bodyToMono(String::class.java).block()) }
    }

    @Test
    fun testMono() {
        val mono = DefaultGraphQLClient(url).reactiveExecuteQuery("{ hello }", emptyMap(), requestExecutor)
        val graphQLResponse = mono.block()
        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertThat(graphQLResponse.data["hello"]).isEqualTo("Hi!")
    }
}