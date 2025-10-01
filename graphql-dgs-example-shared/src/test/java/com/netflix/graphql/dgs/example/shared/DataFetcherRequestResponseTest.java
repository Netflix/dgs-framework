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

package com.netflix.graphql.dgs.example.shared;

import com.netflix.graphql.dgs.autoconfig.DgsExtendedScalarsAutoConfiguration;
import com.netflix.graphql.dgs.client.CustomGraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLRequestOptions;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.GraphqlSSESubscriptionGraphQLClient;
import com.netflix.graphql.dgs.client.HttpResponse;
import com.netflix.graphql.dgs.client.MonoGraphQLClient;
import com.netflix.graphql.dgs.client.RequestExecutor;
import com.netflix.graphql.dgs.client.RestClientGraphQLClient;
import com.netflix.graphql.dgs.example.datafetcher.HelloDataFetcher;
import com.netflix.graphql.dgs.example.shared.dataLoader.MessageDataLoaderWithDispatchPredicate;
import com.netflix.graphql.dgs.example.shared.datafetcher.ConcurrentDataFetcher;
import com.netflix.graphql.dgs.example.shared.datafetcher.CurrentTimeDateFetcher;
import com.netflix.graphql.dgs.example.shared.datafetcher.MovieDataFetcher;
import com.netflix.graphql.dgs.example.shared.datafetcher.RatingMutation;
import com.netflix.graphql.dgs.example.shared.datafetcher.RequestHeadersDataFetcher;
import com.netflix.graphql.dgs.pagination.DgsPaginationAutoConfiguration;
import com.netflix.graphql.dgs.scalars.UploadScalar;
import com.netflix.graphql.dgs.test.EnableDgsTest;
import graphql.scalars.ExtendedScalars;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.test.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalTime;
import java.util.Map;

/**
 * A test for running E2E graphQL request/response tests using the DGS framework in java
 * This test is to explicitly validate that scalar maps and variables passed in as inputs
 * are serialized and deserialized correctly in the request and response using our provided
 * clients.
 */
@SpringBootTest(
        classes = {HelloDataFetcher.class, MovieDataFetcher.class, ConcurrentDataFetcher.class, RequestHeadersDataFetcher.class, RatingMutation.class, CurrentTimeDateFetcher.class, DgsExtendedScalarsAutoConfiguration.class, DgsPaginationAutoConfiguration.class, MessageDataLoaderWithDispatchPredicate.class, UploadScalar.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableDgsTest
@EnableAutoConfiguration
public class DataFetcherRequestResponseTest {

    @LocalServerPort
    Integer port;

    @Autowired
    RestClient.Builder restClientBuilder;

    @Test
    public void webClientScalarsCanBeProvided() {
        MonoGraphQLClient client = MonoGraphQLClient.createWithWebClient(
                WebClient.create("http://localhost:" + port + "/graphql"),
                new GraphQLRequestOptions(
                        Map.of(LocalTime.class, ExtendedScalars.LocalTime.getCoercing())
                )
        );
        LocalTime theTimeIs = LocalTime.of(9, 2, 1);

        String query = "query EchoTimeQuery($input: LocalTime!) { echoTime(time: $input) }";
        GraphQLResponse result = client.reactiveExecuteQuery(query, Map.of("input", theTimeIs)).block();
        Assertions.assertThat(result.extractValueAsObject("echoTime", LocalTime.class)).isEqualTo(theTimeIs);
    }

    @Test
    public void restClientScalarsCanBeProvided() {
        RestClient client = restClientBuilder.baseUrl("http://localhost:" + port + "/graphql").build();
        RestClientGraphQLClient restClient = new RestClientGraphQLClient(
                client,
                new GraphQLRequestOptions(
                        Map.of(LocalTime.class, ExtendedScalars.LocalTime.getCoercing())
                )
        );

        LocalTime theTimeIs = LocalTime.of(9, 2, 1);
        GraphQLResponse result = restClient.executeQuery(
                "query EchoTimeQuery($input: LocalTime!) { echoTime(time: $input) }",
                Map.of("input", theTimeIs)
        );
        Assertions.assertThat(result.extractValueAsObject("echoTime", LocalTime.class)).isEqualTo(theTimeIs);
    }

    @Test
    public void clientConstructorTest() {
        String url = "http://localhost:" + port + "/graphql";
        GraphQLRequestOptions options = new GraphQLRequestOptions(Map.of(LocalTime.class, ExtendedScalars.LocalTime.getCoercing()));
        GraphqlSSESubscriptionGraphQLClient subscriptionGraphQLClient = new GraphqlSSESubscriptionGraphQLClient(url, WebClient.create(url), options);
        GraphqlSSESubscriptionGraphQLClient subscriptionGraphQLClientWithOptions = new GraphqlSSESubscriptionGraphQLClient(url,
                WebClient.create(url), options);
        RequestExecutor dummyExecutor = new RequestExecutor() {
            @Override
            public @NotNull HttpResponse execute(@NotNull String url, @NotNull HttpHeaders headers, @NotNull String body) {
                return null;
            }
        };
        CustomGraphQLClient customGraphQLClient = new CustomGraphQLClient(url, dummyExecutor, options);
    }
}
