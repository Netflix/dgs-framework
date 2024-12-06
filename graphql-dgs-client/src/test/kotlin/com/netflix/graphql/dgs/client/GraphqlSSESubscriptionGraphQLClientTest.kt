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

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsSubscription
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.test.EnableDgsTest
import graphql.language.FieldDefinition.newFieldDefinition
import graphql.language.ObjectTypeDefinition.newObjectTypeDefinition
import graphql.language.TypeName
import graphql.schema.idl.TypeDefinitionRegistry
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

@SpringBootTest(
    classes = [TestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@EnableDgsTest
internal class GraphqlSSESubscriptionGraphQLClientTest {
    @LocalServerPort
    var port: Int? = null

    @Test
    fun `A successful subscription should publish ticks`() {
        val client = GraphqlSSESubscriptionGraphQLClient("/graphql", WebClient.create("http://localhost:$port"))
        val reactiveExecuteQuery =
            client.reactiveExecuteQuery("subscription {numbers}", emptyMap()).mapNotNull { r -> r.data["numbers"] }

        StepVerifier
            .create(reactiveExecuteQuery)
            .expectNext(1, 2, 3)
            .expectComplete()
            .verify()
    }

    @Test
    fun `An error on the subscription should send the error as a response and end the subscription`() {
        val client = GraphqlSSESubscriptionGraphQLClient("/graphql", WebClient.create("http://localhost:$port"))
        val reactiveExecuteQuery = client.reactiveExecuteQuery("subscription {withError}", emptyMap())

        StepVerifier
            .create(reactiveExecuteQuery)
            .consumeNextWith { r -> r.hasErrors() }
            .expectComplete()
            .verify()
    }

    @Test
    fun `A connection error should result in a WebClientException`() {
        val client = GraphqlSSESubscriptionGraphQLClient("/wrongurl", WebClient.create("http://localhost:$port"))

        assertThrows(WebClientResponseException.NotFound::class.java) {
            client.reactiveExecuteQuery("subscription {withError}", emptyMap()).blockLast()
        }
    }

    @Test
    fun `A badly formatted query should result in a WebClientException`() {
        val client = GraphqlSSESubscriptionGraphQLClient("/graphql", WebClient.create("http://localhost:$port"))

        val reactiveExecuteQuery = client.reactiveExecuteQuery("invalid query", emptyMap())
        StepVerifier
            .create(reactiveExecuteQuery)
            .consumeNextWith { r -> r.hasErrors() }
            .expectComplete()
            .verify()
    }

    @Test
    fun `An invalid query should result in a WebClientException`() {
        val client = GraphqlSSESubscriptionGraphQLClient("/graphql", WebClient.create("http://localhost:$port"))

        val reactiveExecuteQuery = client.reactiveExecuteQuery("subscriptions { unkownField }", emptyMap())
        StepVerifier
            .create(reactiveExecuteQuery)
            .consumeNextWith { r -> r.hasErrors() }
            .expectComplete()
            .verify()
    }
}

@SpringBootApplication
internal open class TestApp {
    @DgsComponent
    class SubscriptionDataFetcher {
        @DgsSubscription
        fun numbers(): Flux<Int> = Flux.just(1, 2, 3)

        @DgsSubscription
        fun withError(): Flux<Int> = Flux.error(IllegalArgumentException("testing"), true)

        @DgsTypeDefinitionRegistry
        fun typeDefinitionRegistry(): TypeDefinitionRegistry {
            val newRegistry = TypeDefinitionRegistry()
            newRegistry.add(
                newObjectTypeDefinition()
                    .name("Subscription")
                    .fieldDefinition(
                        newFieldDefinition()
                            .name("numbers")
                            .type(TypeName("Int"))
                            .build(),
                    ).fieldDefinition(
                        newFieldDefinition()
                            .name("withError")
                            .type(TypeName("Int"))
                            .build(),
                    ).build(),
            )
            return newRegistry
        }
    }
}
