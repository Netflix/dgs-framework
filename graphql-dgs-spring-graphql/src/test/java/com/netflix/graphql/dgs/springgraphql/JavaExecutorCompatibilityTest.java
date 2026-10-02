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

package com.netflix.graphql.dgs.springgraphql;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.internal.DefaultDgsGraphQLContextBuilder;
import com.netflix.graphql.dgs.internal.DgsDataLoaderProvider;
import com.netflix.graphql.dgs.internal.Jackson3DgsJsonMapper;
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveGraphQLContextBuilder;
import graphql.ExecutionResultImpl;
import org.dataloader.DataLoaderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class JavaExecutorCompatibilityTest {
    private final List<Map<String, Object>> observedVariables = new ArrayList<>();
    private final ExecutionGraphQlService service = request -> {
        var input = request.toExecutionInput();
        observedVariables.add(input.getVariables());
        var result = ExecutionResultImpl.newExecutionResult()
                .data(Map.of("title", input.getVariables().get("id"), "titles", List.of("Movie")))
                .build();
        return Mono.just(new DefaultExecutionGraphQlResponse(input, result));
    };
    private final DgsDataLoaderProvider dataLoaders = new DgsDataLoaderProvider() {
        @Override
        public DataLoaderRegistry buildRegistry() {
            return new DataLoaderRegistry();
        }

        @Override
        public <T> DataLoaderRegistry buildRegistryWithContextSupplier(Supplier<T> supplier) {
            return buildRegistry();
        }
    };

    @Test
    void blockingConcreteExecutorAcceptsTypedMaps() {
        var executor = new SpringGraphQLDgsQueryExecutor(
                service, new DefaultDgsGraphQLContextBuilder(Optional.empty()), dataLoaders, new Jackson3DgsJsonMapper(), List.of());
        Map<String, String> variables = Map.of("id", "movie-1");
        Map<String, Boolean> extensions = Map.of("enabled", true);

        Map<String, Object> data = executor.execute("{ title }", variables, extensions, null, null, null).getData();

        assertThat(data.get("title")).isEqualTo("movie-1");
        assertThat(observedVariables).containsExactly(Map.of("id", "movie-1"));
    }

    @Test
    void reactiveConcreteExecutorAcceptsTypedMapsForExecutionAndExtraction() {
        var executor = new SpringGraphQLDgsReactiveQueryExecutor(
                service, new DefaultDgsReactiveGraphQLContextBuilder(), dataLoaders, new Jackson3DgsJsonMapper());
        Map<String, String> variables = Map.of("id", "movie-1");
        Map<String, Boolean> extensions = Map.of("enabled", true);
        String query = "{ title titles }";

        Map<String, Object> data = executor.execute(query, variables, extensions, null, null, null).block().getData();
        assertThat(data.get("title")).isEqualTo("movie-1");
        assertThat(executor.<String>executeAndExtractJsonPath(query, "$.data.title", variables, null).block())
                .isEqualTo("movie-1");
        assertThat(executor.executeAndGetDocumentContext(query, variables).block().<String>read("$.data.title"))
                .isEqualTo("movie-1");
        assertThat(executor.executeAndExtractJsonPathAsObject(query, "$.data.title", variables, String.class).block())
                .isEqualTo("movie-1");
        assertThat(executor.executeAndExtractJsonPathAsObject(query, "$.data.titles", variables, new TypeRef<List<String>>() {}).block())
                .containsExactly("Movie");
        assertThat(observedVariables).hasSize(5).allSatisfy(map -> assertThat(map).containsEntry("id", "movie-1"));
    }
}
