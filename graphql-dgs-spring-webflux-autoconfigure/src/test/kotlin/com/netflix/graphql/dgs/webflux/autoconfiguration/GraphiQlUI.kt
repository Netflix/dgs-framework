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

package com.netflix.graphql.dgs.webflux.autoconfiguration

import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.reactive.config.EnableWebFlux

@AutoConfigureWebTestClient
@EnableWebFlux
@SpringBootTest(classes = [DgsWebFluxAutoConfiguration::class, DgsAutoConfiguration::class, WebRequestTestWithCustomEndpoint.ExampleImplementation::class])
class GraphiQlUI {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `GraphiQL should be available`() {
        webTestClient.get().uri("/graphiql/index.html").exchange()
            .expectStatus().isOk
    }

    @Test
    fun `graphiql should redirect to correct page`() {
        webTestClient.get().uri("/graphiql").exchange()
            .expectStatus().isPermanentRedirect
    }

    @Test
    fun `graphiql should load page when URI has no query parameters`() {
        webTestClient.get().uri("/graphiql/index.html").exchange()
            .expectStatus().isOk
    }

    @Test
    fun `graphiql title should be default`() {
        webTestClient.get().uri("/graphiql/index.html")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<String>()
            .consumeWith {
                Assertions.assertThat(it.responseBody.orEmpty())
                    .contains("Simple GraphiQL Example")
            }
    }
}
