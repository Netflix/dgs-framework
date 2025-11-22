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

package com.netflix.graphql.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinFeature;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.netflix.graphql.dgs.client.CustomGraphQLClient;
import com.netflix.graphql.dgs.client.CustomMonoGraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLRequestOptions;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.HttpResponse;
import com.netflix.graphql.dgs.client.MonoGraphQLClient;
import com.netflix.graphql.dgs.client.RequestExecutor;
import kotlin.Unit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static com.fasterxml.jackson.module.kotlin.ExtensionsKt.kotlinModule;

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

    RequestExecutor requestExecutor = (url, headers, body) -> {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach(httpHeaders::addAll);
        ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, httpHeaders), String.class);
        return new HttpResponse(exchange.getStatusCode().value(), exchange.getBody(), toMap(exchange.getHeaders()));
    };

    CustomGraphQLClient client = new CustomGraphQLClient(url, requestExecutor, new GraphQLRequestOptions());

    @Test
    public void responseWithoutHeaders() {

        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"operationName\":\"SubmitReview\"}"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        GraphQLResponse graphQLResponse = client.executeQuery(
                query,
                emptyMap(), "SubmitReview"
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

       GraphQLResponse graphQLResponse = client.executeQuery(query, emptyMap());

        String submittedBy = graphQLResponse.extractValueAsObject("submitReview.submittedBy", String.class);
        assertThat(submittedBy).isEqualTo("abc@netflix.com");
        assertThat(graphQLResponse.getHeaders().get("Content-Type").get(0)).isEqualTo("application/json");
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
        assertThat(submittedBy).isEqualTo("abc@netflix.com");
        server.verify();
    }

    @Test
    public void testCustomWithOptions() {
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"operationName\":\"SubmitReview\"}"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        CustomGraphQLClient client = GraphQLClient.createCustom(url, requestExecutor, new GraphQLRequestOptions());
        GraphQLResponse graphQLResponse = client.executeQuery(query, emptyMap(), "SubmitReview");
        String submittedBy = graphQLResponse.extractValueAsObject("submitReview.submittedBy", String.class);
        assertThat(submittedBy).isEqualTo("abc@netflix.com");
        server.verify();
    }

    @Test
    public void testCustomObjectMapperIsRetained() {
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"operationName\":\"SubmitReview\"}"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));


        @SuppressWarnings("deprecation")
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder
                .json()
                .modulesToInstall(
                        new KotlinModule.Builder()
                                .enable(KotlinFeature.NullIsSameAsDefault)
                                .build()
                )
                .build();

        CustomGraphQLClient client = GraphQLClient.createCustom(url, requestExecutor, objectMapper);
        GraphQLResponse graphQLResponse = client.executeQuery(query, emptyMap(), "SubmitReview");
        String submittedBy = graphQLResponse.extractValueAsObject("submitReview.submittedBy", String.class);
        assertThat(submittedBy).isEqualTo("abc@netflix.com");
        server.verify();

        try {
            // Use reflection to access the private 'mapper' field in GraphQLResponse
            Field mapperField = GraphQLResponse.class.getDeclaredField("mapper");
            mapperField.setAccessible(true);
            ObjectMapper responseMapper = (ObjectMapper) mapperField.get(graphQLResponse);

            // Assert that the ObjectMapper in the response is the same as the custom one
            assertThat(responseMapper).isSameAs(objectMapper);
        } catch (Exception e) {
           fail("Shouldn't fail", e);
        }

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
            ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, httpHeaders), String.class);
            return Mono.just(new HttpResponse(exchange.getStatusCode().value(), exchange.getBody(), toMap(exchange.getHeaders())));
        }, new GraphQLRequestOptions());
        Mono<GraphQLResponse> graphQLResponse = client.reactiveExecuteQuery(query, emptyMap(), "SubmitReview");
        String submittedBy = graphQLResponse.map(r -> r.extractValueAsObject("submitReview.submittedBy", String.class)).block();
        assertThat(submittedBy).isEqualTo("abc@netflix.com");
        server.verify();
    }

    private static Map<String, List<String>> toMap(HttpHeaders headers) {
        Map<String, List<String>> result = new HashMap<>();
        headers.forEach(result::put);
        return result;
    }
}
