/*
 * Copyright 2023 Netflix, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.example.shared.types.Stock;
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor;
import graphql.ExecutionResult;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class SubscriptionTest {

    @Autowired
    DgsReactiveQueryExecutor queryExecutor;

    ObjectMapper objectMapper = new ObjectMapper();


    @Test
    void stocks() {
        var executionResult = queryExecutor.execute("subscription Stocks { stocks { name, price } }");

        Publisher<ExecutionResult> publisher = executionResult.flatMap(ExecutionResult::getData);

        StepVerifier.withVirtualTime(() -> publisher, 3)
                .expectSubscription()
                .thenRequest(3)

                .assertNext(result -> assertThat(toStock(result).getPrice()).isEqualTo(500))
                .assertNext(result -> assertThat(toStock(result).getPrice()).isEqualTo(501))
                .assertNext(result -> assertThat(toStock(result).getPrice()).isEqualTo(502))
                .thenCancel()
                .verify();
    }

    private Stock toStock(ExecutionResult result) {
        Map<String, Object> data = result.getData();
        return objectMapper.convertValue(data.get("stocks"), Stock.class);
    }
}
