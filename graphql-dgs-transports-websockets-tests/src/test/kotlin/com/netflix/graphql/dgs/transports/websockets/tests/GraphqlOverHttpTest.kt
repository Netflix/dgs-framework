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
import com.apollographql.apollo3.network.http.HttpNetworkTransport
import com.apollographql.apollo3.testing.runTest
import com.example.client.HelloQuery
import com.example.client.SetHelloMutation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.web.server.LocalServerPort

abstract class GraphqlOverHttpTest {
    @LocalServerPort
    protected lateinit var port: Integer

    private lateinit var apolloClient: ApolloClient

    @BeforeEach
    fun setup() {
        apolloClient = ApolloClient.Builder()
            .networkTransport(
                HttpNetworkTransport.Builder().serverUrl(
                    serverUrl = "http://localhost:$port/graphql",
                ).build()
            )
            .build()
    }

    @AfterEach
    fun teardown() {
        apolloClient.dispose()
    }

    @Test
    fun queryOverHttp() = runTest {
        assertEquals("Hello World!", apolloClient.query(HelloQuery()).execute().data?.hello)
    }

    @Test
    fun mutationOverHttp() = runTest {
        assertEquals("Hello Mutation!", apolloClient.mutation(SetHelloMutation()).execute().data?.hello)
    }
}
