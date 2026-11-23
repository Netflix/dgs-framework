/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.graphql.dgs.example.reactive;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.DataLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@SpringBootTest
class BatchLoaderRegistryDispatchTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void dgsDataLoaderIsDispatched() {
        execute("{ messageFromBatchLoader }")
                .jsonPath("$.data.messageFromBatchLoader").isEqualTo("hello, a!");
    }

    @Test
    void batchLoaderRegistryLoaderIsDispatched() {
        execute("{ springBatchLoaderGreeting(name: \"DGS\") }")
                .jsonPath("$.data.springBatchLoaderGreeting").isEqualTo("greetings DGS");
    }

    private WebTestClient.BodyContentSpec execute(String query) {
        return WebTestClient.bindToApplicationContext(context).configureClient()
                .responseTimeout(Duration.ofSeconds(5)).build()
                .post().uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query))
                .exchange()
                .expectStatus().isOk()
                .expectBody();
    }

    @TestConfiguration
    static class Config {
        @Bean
        Object springBatchLoaderRegistration(BatchLoaderRegistry registry) {
            registry.<String, String>forName("springGreetings").registerMappedBatchLoader((names, env) ->
                    Mono.just(names.stream().collect(Collectors.toMap(n -> n, n -> "greetings " + n))));
            return new Object();
        }

        @Bean
        Fetcher springBatchLoaderFetcher() {
            return new Fetcher();
        }
    }

    @DgsComponent
    static class Fetcher {
        @DgsQuery
        public CompletableFuture<String> springBatchLoaderGreeting(@InputArgument String name, DataFetchingEnvironment env) {
            DataLoader<String, String> loader = env.getDataLoader("springGreetings");
            return loader.load(name);
        }
    }
}
