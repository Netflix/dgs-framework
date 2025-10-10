/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.graphql.dgs.example.datafetcher;

import com.netflix.graphql.dgs.example.shared.types.Stock;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.test.LocalServerPort;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.WebSocketGraphQlTester;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.net.URI;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SubscriptionsGraphQlTesterTest {

    @LocalServerPort
    private int port;

    @Value("http://localhost:${local.server.port}/graphql")
    private String baseUrl;

    private GraphQlTester graphQlTester;


    @BeforeEach
    void setUp() {
        URI url = URI.create(baseUrl);
        this.graphQlTester = WebSocketGraphQlTester.builder(url, new ReactorNettyWebSocketClient()).build();
    }

    @Test
    void stocks() {
        Flux<Stock> result = graphQlTester.document("subscription Stocks { stocks { name, price } }").executeSubscription().toFlux("stocks", Stock.class);

        StepVerifier.create(result)
                .assertNext(res -> Assertions.assertThat(res.getPrice()).isEqualTo(500))
                .assertNext(res -> Assertions.assertThat(res.getPrice()).isEqualTo(501))
                .assertNext(res -> Assertions.assertThat(res.getPrice()).isEqualTo(502))
                .thenCancel()
                .verify();
    }
}
