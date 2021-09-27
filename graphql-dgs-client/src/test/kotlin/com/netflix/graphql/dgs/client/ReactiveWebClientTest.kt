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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Suppress("DEPRECATION")
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
            .exchange()
            .flatMap { cr -> cr.bodyToMono(String::class.java).map { json -> HttpResponse(cr.rawStatusCode(), json) } }
    }

    @Test
    fun testMono() {
        val mono = DefaultGraphQLClient(url).reactiveExecuteQuery("{ hello }", emptyMap(), requestExecutor)
        val graphQLResponse = mono.block()
        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertThat(graphQLResponse!!.data["hello"]).isEqualTo("Hi!")
    }
}
