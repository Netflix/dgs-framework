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

package com.netflix.graphql.dgs.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.types.subscription.QueryPayload;
import org.intellij.lang.annotations.Language;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * This client can be used for servers which are following the subscriptions-transport-sse specification, which can be
 * found here: https://github.com/CodeCommission/subscriptions-transport-sse
 *
 * @deprecated This client uses the obsolete subscriptions-transport-sse protocol. Use
 *             {@link GraphqlSSESubscriptionGraphQLClient} to use the newer graphql-sse spec.
 */
@Deprecated
public class SSESubscriptionGraphQLClient implements ReactiveGraphQLClient {
    private final String url;
    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public SSESubscriptionGraphQLClient(String url, WebClient webClient) {
        this.url = url;
        this.webClient = webClient;
    }

    @Override
    public Flux<GraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query, Map<String, Object> variables) {
        return reactiveExecuteQuery(query, variables, null);
    }

    @Override
    public Flux<GraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query, Map<String, Object> variables, String operationName) {
        QueryPayload queryPayload = new QueryPayload(variables, Map.of(), operationName, query);

        String jsonPayload;
        try {
            jsonPayload = mapper.writeValueAsString(queryPayload);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        return webClient
                .get()
                .uri(url + "?query={query}", Map.of("query", encodeQuery(jsonPayload)))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .toEntityFlux(String.class)
                .flatMapMany(response -> {
                    Map<String, List<String>> headers = HttpHeaderUtils.toMap(response.getHeaders());
                    Flux<String> body = response.getBody();
                    return body != null
                            ? body.map(content -> new GraphQLResponse(content, headers))
                            : Flux.<GraphQLResponse>empty();
                })
                .publishOn(Schedulers.single());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castVariables(Map<String, Object> variables) {
        return variables;
    }

    private String encodeQuery(@Language("graphql") String query) {
        return Base64.getEncoder().encodeToString(query.getBytes(StandardCharsets.UTF_8));
    }
}
