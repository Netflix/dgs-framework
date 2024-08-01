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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.LocalDate

class ReactiveWebClientTest {
    private val url = "http://test"

    private val requestExecutor = MonoRequestExecutor { url, headers, body ->
        WebClient.builder()
            .exchangeFunction {
                Mono.just(
                    ClientResponse.create(HttpStatus.OK)
                        .header("content-type", "application/json")
                        .body("""{ "data": { "hello": "Hi!"}}""")
                        .build()
                )
            }.build()
            .post()
            .uri(url)
            .headers { consumer -> headers.forEach { consumer.addAll(it.key, it.value) } }
            .bodyValue(body)
            .retrieve()
            .toEntity(String::class.java)
            .map { response -> HttpResponse(response.statusCode.value(), response.body) }
    }

    @Test
    fun testReactiveExecuteQuery() {
        val responseMono = CustomMonoGraphQLClient(url, requestExecutor).reactiveExecuteQuery("{ hello }", emptyMap())
        val graphQLResponse = responseMono.block(Duration.ZERO) ?: fail("Expected non-null response")
        assertThat(graphQLResponse.data["hello"]).isEqualTo("Hi!")
    }

    @Test
    fun testCustomObjectMapper() {
        val mapper = ObjectMapper().registerModules(JavaTimeModule())
        val responseMono = CustomMonoGraphQLClient(url, requestExecutor, mapper).reactiveExecuteQuery(
            "{ hello(${'$'}input: HelloInput!) }",
            mapOf("foo" to LocalDate.now())
        )
        val graphQLResponse = responseMono.block(Duration.ZERO) ?: fail("Expected non-null response")
        assertThat(graphQLResponse.data["hello"]).isEqualTo("Hi!")
    }
}
