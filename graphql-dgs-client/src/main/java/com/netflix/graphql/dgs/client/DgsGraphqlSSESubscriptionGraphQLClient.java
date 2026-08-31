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

import com.netflix.graphql.dgs.json.DgsJsonMapper;
import com.netflix.graphql.types.subscription.QueryPayload;
import org.intellij.lang.annotations.Language;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * Client for servers following the graphql-sse spec
 * (https://github.com/graphql/graphql-over-http/blob/main/rfcs/GraphQLOverSSE.md).
 * The no-arg-mapper convenience constructor uses Jackson 3 under the hood. Callers on Jackson 2
 * must pass {@link Jackson2DgsJsonMapperAdapter} explicitly.
 */
public class DgsGraphqlSSESubscriptionGraphQLClient implements DgsReactiveGraphQLClient {
    private final String url;
    private final WebClient webClient;
    private final DgsJsonMapper mapper;

    public DgsGraphqlSSESubscriptionGraphQLClient(String url, WebClient webClient, DgsJsonMapper mapper) {
        this.url = url;
        this.webClient = webClient;
        this.mapper = mapper;
    }

    public DgsGraphqlSSESubscriptionGraphQLClient(String url, WebClient webClient) {
        this(url, webClient, Jackson3DgsJsonMapperAdapter.defaultMapper());
    }

    public DgsGraphqlSSESubscriptionGraphQLClient(
            String url, WebClient webClient, DgsGraphQLRequestOptions options) {
        this(url, webClient, Jackson3DgsJsonMapperAdapter.fromOptions(options));
    }

    @Override
    public Flux<? extends DgsGraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query, Map<String, Object> variables) {
        return reactiveExecuteQuery(query, variables, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Flux<? extends DgsGraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query, Map<String, Object> variables, String operationName) {
        QueryPayload queryPayload =
                new QueryPayload(variables, Map.of(), operationName, query);
        String jsonPayload = mapper.writeValueAsString(queryPayload);
        Sinks.Many<DgsGraphQLResponse> sink = Sinks.many().unicast().onBackpressureBuffer();

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
                                    ? body.filter(sse -> !sse.isBlank())
                                            .map(sse -> sink.tryEmitNext(
                                                    new DefaultDgsGraphQLResponse(sse, headers, mapper)))
                                    : Flux.empty();
                        })
                        .onErrorResume(throwable -> Flux.just(sink.tryEmitError(throwable)))
                        .doFinally(signalType -> sink.tryEmitComplete())
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
        return sink.asFlux().publishOn(Schedulers.single()).doFinally(signalType -> dis.dispose());
    }
}
