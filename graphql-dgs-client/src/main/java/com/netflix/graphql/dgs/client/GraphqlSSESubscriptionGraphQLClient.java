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
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * This client can be used for servers which are following the graphql-sse specification, which can be found here:
 * https://github.com/graphql/graphql-over-http/blob/main/rfcs/GraphQLOverSSE.md
 *
 * @deprecated Tied to Jackson 2. Migrate to {@link DgsGraphqlSSESubscriptionGraphQLClient}, which accepts any
 *             {@code DgsJsonMapper} (defaulting to Jackson 3). This class will be removed in a future release.
 */
@Deprecated
public class GraphqlSSESubscriptionGraphQLClient implements ReactiveGraphQLClient {
    private final String url;
    private final WebClient webClient;
    private final ObjectMapper mapper;

    public GraphqlSSESubscriptionGraphQLClient(String url, WebClient webClient, GraphQLRequestOptions options) {
        this.url = url;
        this.webClient = webClient;
        this.mapper = GraphQLRequestOptions.createCustomObjectMapper(options);
    }

    public GraphqlSSESubscriptionGraphQLClient(String url, WebClient webClient) {
        this(url, webClient, null);
    }

    @Override
    public Flux<GraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query, Map<String, Object> variables) {
        return reactiveExecuteQuery(query, variables, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Flux<GraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query, Map<String, Object> variables, String operationName) {
        QueryPayload queryPayload =
                new QueryPayload(variables, Map.of(), operationName, query);

        String jsonPayload;
        try {
            jsonPayload = mapper.writeValueAsString(queryPayload);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        Sinks.Many<GraphQLResponse> sink = Sinks.many().unicast().onBackpressureBuffer();

        Disposable dis =
                webClient
                        .post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(jsonPayload)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .retrieve()
                        .toEntityFlux(String.class)
                        .flatMapMany(responseEntity -> {
                            Map<String, List<String>> headers =
                                    HttpHeaderUtils.toMap(responseEntity.getHeaders());
                            Flux<String> body = responseEntity.getBody();
                            return body != null
                                    ? body.filter(serverSentEvent -> !serverSentEvent.isBlank())
                                            .map(serverSentEvent -> sink.tryEmitNext(
                                                    new GraphQLResponse(serverSentEvent, headers, mapper)))
                                    : Flux.empty();
                        })
                        .onErrorResume(throwable -> Flux.just(sink.tryEmitError(throwable)))
                        .doFinally(signalType -> sink.tryEmitComplete())
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
        return sink.asFlux().publishOn(Schedulers.single()).doFinally(signalType -> dis.dispose());
    }
}
