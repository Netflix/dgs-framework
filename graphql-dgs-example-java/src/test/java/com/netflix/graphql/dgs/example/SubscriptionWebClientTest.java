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

package com.netflix.graphql.dgs.example;

import com.netflix.graphql.dgs.client.ReactiveGraphQLClient;
import com.netflix.graphql.dgs.client.SSESubscriptionGraphQLClient;
import com.netflix.graphql.dgs.client.WebSocketGraphQLClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SubscriptionWebClientTest {

    @LocalServerPort
    Integer port;

    @Test
    public void testWSSubscription() {
        WebSocketGraphQLClient client = new WebSocketGraphQLClient("ws://localhost:" + port + "/subscriptions", new ReactorNettyWebSocketClient());

        assertResult(client);
    }

    @Test
    public void testSSESubscription() {
        WebClient build = WebClient.builder().baseUrl("http://localhost:" + port).build();
        SSESubscriptionGraphQLClient client = new SSESubscriptionGraphQLClient("/subscriptions", build);
        assertResult(client);

    }

    private void assertResult(ReactiveGraphQLClient client) {
        Flux<Double> graphQLResponseFlux = client.reactiveExecuteQuery("subscription { stocks {name price}}", new HashMap<>()).map(r -> r.extractValue("data.stocks.price"));
        Flux<Double> graphQLResponseFlux2 = client.reactiveExecuteQuery("subscription { stocks {name price}}", new HashMap<>()).map(r -> r.extractValue("data.stocks.price"));
         Flux.combineLatest(graphQLResponseFlux, graphQLResponseFlux2, (a, b) -> "A: " + a + ", B: " + b).log().take(3).subscribeOn(Schedulers.parallel()).blockLast();

//        StepVerifier.create(graphQLResponseFlux).expectNext(500.0, 501.0, 502.0).expectComplete().verify();
//        StepVerifier.create(graphQLResponseFlux2).expectNext(500.0, 501.0, 502.0).expectComplete().verify();

//        Flux<GraphQLResponse> graphQLResponseFlux2 = client.reactiveExecuteQuery("subscription { stocks {name price}}", new HashMap<>());
//        Flux<Double> flux2 = graphQLResponseFlux2.map(r -> r.extractValue("data.stocks.price"));
//
//
//        Flux<String> take = flux1.zipWith(flux2, (a, b) -> "A: " + a + ", B: " + b).take(3);
//        take.log().blockLast();
//        StepVerifier.create(zip)
//                .expectNext("A: 500.0, B: 500.0")
//                .expectNext("A: 501.0, B: 501.0")
//                .expectNext("A: 502.0, B: 502.0")
//                .expectComplete()
//                .verify();
    }
}
