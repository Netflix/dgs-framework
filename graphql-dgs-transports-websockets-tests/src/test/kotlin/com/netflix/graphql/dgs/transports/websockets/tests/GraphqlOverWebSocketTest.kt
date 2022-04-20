/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.graphql.dgs.transports.websockets.tests

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.network.ws.GraphQLWsProtocol
import com.apollographql.apollo3.network.ws.WebSocketNetworkTransport
import com.apollographql.apollo3.testing.runTest
import com.example.client.GreetingsSubscription
import com.example.client.HelloQuery
import com.example.client.SetHelloMutation
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.boot.web.server.LocalServerPort
import java.util.concurrent.TimeUnit

abstract class GraphqlOverWebSocketTest {
    @LocalServerPort
    private lateinit var port: Integer

    private lateinit var apolloClient: ApolloClient

    @BeforeEach
    fun setup() {
        apolloClient = ApolloClient.Builder()
            .networkTransport(
                WebSocketNetworkTransport.Builder().serverUrl(
                    serverUrl = "http://localhost:$port/graphql",
                ).protocol(
                    protocolFactory = GraphQLWsProtocol.Factory()
                ).build()
            )
            .build()

        // apolloClient.networkTransport.pin
    }

    @Test
    @Timeout(value = 1000, unit = TimeUnit.MILLISECONDS)
    fun queryOverWebSocket() = runTest {

        assertEquals("Hello World!", apolloClient.query(HelloQuery()).execute().data?.hello)
    }

    @Test
    @Timeout(value = 1000, unit = TimeUnit.MILLISECONDS)
    fun mutationOverWebSocket() = runTest {

        assertEquals("Hello Mutation!", apolloClient.mutation(SetHelloMutation()).execute().data?.hello)
    }

    @Test
    @Timeout(value = 1000, unit = TimeUnit.MILLISECONDS)
    fun subscriptionOverWebSocket() = runTest {

        val list = apolloClient.subscription(GreetingsSubscription())
            .toFlow()
            .toList()
        assertEquals(listOf("Hi", "Bonjour", "Hola", "Ciao", "Zdravo"), list.map { it.data?.greetings })

        apolloClient.dispose()
    }
}
