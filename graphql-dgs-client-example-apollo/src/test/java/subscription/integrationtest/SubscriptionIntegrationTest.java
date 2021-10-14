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

package subscription.integrationtest;


import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.netflix.graphql.dgs.client.WebSocketGraphQLClient;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;


@Testcontainers
public class SubscriptionIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionIntegrationTest.class);

    @Container
    private static final GenericContainer<?> apolloContainer = new GenericContainer<>(
            "amond/graphql-dgs-client-example-apollo"
            /*new ImageFromDockerfile()
                    .withFileFromPath("package.json", Paths.get("apollo/package.json"))
                    .withFileFromPath("package-lock.json", Paths.get("apollo/package-lock.json"))
                    .withFileFromPath("app.js", Paths.get("apollo/app.js"))
                    .withDockerfile(Paths.get("apollo/Dockerfile"))*/
    ).withExposedPorts(4000).withCreateContainerCmdModifier(cmd -> cmd.withHostConfig(
                    new HostConfig().withPortBindings(new PortBinding(Ports.Binding.bindPort(4000), new ExposedPort(4000)))
            )).withStartupTimeout(Duration.ofSeconds(20))
            .withLogConsumer(new Slf4jLogConsumer(logger));


    private WebSocketGraphQLClient webSocketGraphQLClient;

    @BeforeEach
    public void setup() {
        webSocketGraphQLClient = new WebSocketGraphQLClient("ws://" + apolloContainer.getHost() + ":" + apolloContainer.getMappedPort(4000) + "/graphql", new ReactorNettyWebSocketClient());
    }


    @Test
    public void testWebSocketSubscription() {
        String subscriptionRequest = "subscription StockWatch { stocks { name price } }";
        Flux<Double> starScore = webSocketGraphQLClient.reactiveExecuteQuery(subscriptionRequest, Collections.emptyMap()).take(3).map(r -> r.extractValue("stocks.price"));
        StepVerifier.create(starScore)
                .expectNext(500.5)
                .expectNext(501.5)
                .expectNext(502.5)
                .thenCancel()
                .verify();
    }

}
