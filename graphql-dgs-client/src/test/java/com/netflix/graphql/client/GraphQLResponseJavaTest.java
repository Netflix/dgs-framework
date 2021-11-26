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

package com.netflix.graphql.client;

import com.netflix.graphql.dgs.client.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static java.util.Collections.emptyMap;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SuppressWarnings("deprecation")
public class GraphQLResponseJavaTest {

    private final String query = "query SubmitReview {" +
            "submitReview(review:{movieId:1, description:\"\"}) {" +
            "submittedBy" +
            "}" +
            "}";
    private final String jsonResponse = "{" +
            "\"data\": {" +
            "\"submitReview\": {" +
            "\"submittedBy\": \"abc@netflix.com\"" +
            "}" +
            "}" +
            "}";
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

    String url = "http://localhost:8080/graphql";

    DefaultGraphQLClient client = new DefaultGraphQLClient(url);

    RequestExecutor requestExecutor = (url, headers, body) -> {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach(httpHeaders::addAll);
        ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, httpHeaders),String.class);
        return new HttpResponse(exchange.getStatusCodeValue(), exchange.getBody());
    };

    RequestExecutor requestExecutorWithResponseHeaders = (url, headers, body) -> {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach(httpHeaders::addAll);
        ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, httpHeaders),String.class);
        return new HttpResponse(exchange.getStatusCodeValue(), exchange.getBody(), exchange.getHeaders());
    };

    @Test
    public void responseWithoutHeaders() {

        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"operationName\":\"SubmitReview\"}"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        GraphQLResponse graphQLResponse = client.executeQuery(
                query,
                emptyMap(), "SubmitReview", requestExecutor
        );

        String submittedBy = graphQLResponse.extractValueAsObject("submitReview.submittedBy", String.class);
        assert(submittedBy).contentEquals("abc@netflix.com");
        server.verify();
    }

    @Test
    public void responseWithHeaders() {
       String jsonResponse = "{" +
             "\"data\": {" +
                "\"submitReview\": {" +
                   "\"submittedBy\": \"abc@netflix.com\"" +
                "}" +
              "}" +
            "}";

        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

       GraphQLResponse graphQLResponse = client.executeQuery(query, emptyMap(), requestExecutorWithResponseHeaders);

        String submittedBy = graphQLResponse.extractValueAsObject("submitReview.submittedBy", String.class);
        assert(submittedBy).contentEquals("abc@netflix.com");
        assert(graphQLResponse.getHeaders().get("Content-Type").get(0)).contentEquals("application/json");
        server.verify();
    }

    @Test
    public void testCustom() {
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"operationName\":\"SubmitReview\"}"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        CustomGraphQLClient client = GraphQLClient.createCustom(url, requestExecutor);
        GraphQLResponse graphQLResponse = client.executeQuery(query, emptyMap(), "SubmitReview");
        String submittedBy = graphQLResponse.extractValueAsObject("submitReview.submittedBy", String.class);
        assert(submittedBy).contentEquals("abc@netflix.com");
        server.verify();
    }

    @Test
    public void testCustomMono() {
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"operationName\":\"SubmitReview\"}"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        CustomMonoGraphQLClient client = MonoGraphQLClient.createCustomReactive(url, (requestUrl, headers, body) -> {
            HttpHeaders httpHeaders = new HttpHeaders();
            headers.forEach(httpHeaders::addAll);
            ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, httpHeaders),String.class);
            return Mono.just(new HttpResponse(exchange.getStatusCodeValue(), exchange.getBody(), exchange.getHeaders()));
        });
        Mono<GraphQLResponse> graphQLResponse = client.reactiveExecuteQuery(query, emptyMap(), "SubmitReview");
        String submittedBy = graphQLResponse.map(r -> r.extractValueAsObject("submitReview.submittedBy", String.class)).block();
        assert(submittedBy).contentEquals("abc@netflix.com");
        server.verify();
    }
}
