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

package com.netflix.graphql.dgs.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.Coercing;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
class JavaApiCompatibilityTest {
    @Test
    void typedVariableMapsWorkThroughBlockingAndReactiveClientInterfaces() throws Exception {
        Map<String, String> variables = Map.of("id", "movie-1");
        Map<String, ArrayList<String>> headers = Map.of("x-request-id", new ArrayList<>(List.of("request-1")));
        var requests = new ArrayList<String>();
        RequestExecutor executor = (url, requestHeaders, body) -> {
            requests.add(body);
            return new HttpResponse(200, "{\"data\":{\"title\":\"Movie\"}}", headers);
        };
        MonoRequestExecutor monoExecutor = (url, requestHeaders, body) ->
                Mono.fromSupplier(() -> executor.execute(url, requestHeaders, body));
        var mapper = new ObjectMapper();
        var adapter = new Jackson2DgsJsonMapperAdapter(mapper);
        var query = "query Movie($id: ID!) { title(id: $id) }";

        DgsGraphQLClient client = new DgsCustomGraphQLClient("http://example/graphql", executor, adapter);
        GraphQLClient legacyClient = new CustomGraphQLClient("http://example/graphql", executor, mapper);
        DgsMonoGraphQLClient monoClient = new DgsCustomMonoGraphQLClient("http://example/graphql", monoExecutor, adapter);
        MonoGraphQLClient legacyMonoClient = new CustomMonoGraphQLClient("http://example/graphql", monoExecutor, mapper);

        var responses = List.of(
                client.executeQuery(query, variables),
                legacyClient.executeQuery(query, variables),
                monoClient.reactiveExecuteQuery(query, variables).block(),
                legacyMonoClient.reactiveExecuteQuery(query, variables).block());

        assertThat(requests).hasSize(4);
        for (var request : requests) {
            assertThat(mapper.readTree(request).path("variables").path("id").asText()).isEqualTo("movie-1");
        }
        for (var response : responses) {
            assertThat(response.<String>extractValue("title")).isEqualTo("Movie");
            assertThat(response.getHeaders().get("x-request-id")).containsExactly("request-1");
        }
    }

    @Test
    void scalarOptionConstructorsAcceptMapsOfConcreteCoercingTypes() {
        var coercing = new Coercing<String, String>() {};
        Map<Class<?>, Coercing<String, String>> scalars = Map.of(String.class, coercing);

        Map<Class<?>, Coercing<?, ?>> resolved = new DgsGraphQLRequestOptions(scalars).getScalars();
        Map<Class<?>, Coercing<?, ?>> legacyResolved = new GraphQLRequestOptions(scalars).getScalars();
        assertThat(resolved.get(String.class)).isSameAs(coercing);
        assertThat(legacyResolved.get(String.class)).isSameAs(coercing);
    }
}
