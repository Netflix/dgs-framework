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

package subsciption.integrationtest;


import com.netflix.graphql.dgs.client.WebSocketGraphQLClient;
import com.netflix.graphql.dgs.example.shared.datafetcher.SubscriptionDataFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Collections;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {SubscriptionDataFetcher.class})
@EnableAutoConfiguration
public class SubscriptionIntegrationTest {

    @LocalServerPort
    private Integer port;

    private WebSocketGraphQLClient webSocketGraphQLClient;

    @BeforeEach
    public void setup() {
        webSocketGraphQLClient = new WebSocketGraphQLClient("ws://localhost:" + port + "/subscriptions", new ReactorNettyWebSocketClient());
    }

    @Test
    public void testWebSocketSubscription() {
        String subscriptionRequest = "subscription StockWatch { stocks { name price } }";
        Flux<Double> starScore = webSocketGraphQLClient.reactiveExecuteQuery(subscriptionRequest, Collections.emptyMap()).take(3).map(r -> r.extractValue("stocks.price"));
        StepVerifier.create(starScore)
                .expectNext(500.0)
                .expectNext(501.0)
                .expectNext(502.0)
                .thenCancel()
                .verify();
    }
}
