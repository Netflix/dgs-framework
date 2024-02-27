/*
 * Copyright 2024 Netflix, Inc.
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * This test exposes a bug with how the DGSContext is set up. For normal flow, we end up using DgsWebMvcGraphQlInterceptor that copies DgsContext into GraphQLContext.
 * For subscriptions, this is actually incorrect. This test exposes this issue since for tests, there is no WebMvcGraphQlInterceptor that gets set up.
 * Therefore, the test fails because GraphQlContextContributorInstrumentation tries to extract the context, and it isn't there.
 * SpringGraphQlQueryExecutor handles this fine by copying the context and hence we do not run into this issue with query executor tests.
 */
@SpringBootTest()
@AutoConfigureGraphQlTester
@Disabled
public class SubscriptionsGraphQlTesterTest {

    @Autowired
    private GraphQlTester graphQlTester;

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
