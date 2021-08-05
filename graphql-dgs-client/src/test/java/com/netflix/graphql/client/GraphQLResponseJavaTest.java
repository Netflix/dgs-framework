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

import com.netflix.graphql.dgs.client.DefaultGraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.HttpResponse;
import com.netflix.graphql.dgs.client.RequestExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static java.util.Collections.emptyMap;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class GraphQLResponseJavaTest {

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
                .andExpect(content().json("{\"operationName\":\"SubmitReview\"}"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        GraphQLResponse graphQLResponse = client.executeQuery(
                "query SubmitReview {" +
                        "submitReview(review:{movieId:1, description:\"\"}) {" +
                        "submittedBy" +
                        "}" +
                        "}",
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

       GraphQLResponse graphQLResponse = client.executeQuery(
                "query {" +
                  "submitReview(review:{movieId:1, description:\"\"}) {" +
                   "submittedBy" +
                  "}" +
                "}",
                emptyMap(), requestExecutorWithResponseHeaders
        );

        String submittedBy = graphQLResponse.extractValueAsObject("submitReview.submittedBy", String.class);
        assert(submittedBy).contentEquals("abc@netflix.com");
        assert(graphQLResponse.getHeaders().get("Content-Type").get(0)).contentEquals("application/json");
        server.verify();
    }

}
