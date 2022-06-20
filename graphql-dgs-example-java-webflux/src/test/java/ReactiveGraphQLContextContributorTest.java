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

import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration;
import com.netflix.graphql.dgs.example.context.MyContextContributor;
import com.netflix.graphql.dgs.example.instrumentation.ExampleInstrumentation;
import com.netflix.graphql.dgs.example.shared.datafetcher.MovieDataFetcher;
import com.netflix.graphql.dgs.pagination.DgsPaginationAutoConfiguration;
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor;
import com.netflix.graphql.dgs.webflux.autoconfiguration.DgsWebFluxAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.config.WebFluxConfigurationSupport;
import reactor.core.publisher.Mono;

import static com.netflix.graphql.dgs.example.context.MyContextContributor.CONTEXT_CONTRIBUTOR_HEADER_NAME;
import static com.netflix.graphql.dgs.example.context.MyContextContributor.CONTEXT_CONTRIBUTOR_HEADER_VALUE;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {WebFluxConfigurationSupport.class, DgsAutoConfiguration.class, MovieDataFetcher.class, MyContextContributor.class, ExampleInstrumentation.class, DgsWebFluxAutoConfiguration.class, DgsPaginationAutoConfiguration.class})
public class ReactiveGraphQLContextContributorTest {

    @Autowired
    DgsReactiveQueryExecutor queryExecutor;

    @Test
    void moviesExtensionShouldHaveContributedEnabledExtension() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(CONTEXT_CONTRIBUTOR_HEADER_NAME, CONTEXT_CONTRIBUTOR_HEADER_VALUE);

        final MockServerRequest.Builder builder = MockServerRequest.builder();
        builder.header(CONTEXT_CONTRIBUTOR_HEADER_NAME, CONTEXT_CONTRIBUTOR_HEADER_VALUE);

        Mono<String> contributorEnabled = queryExecutor.executeAndExtractJsonPath(
                "{ movies { director } }",
                "extensions.contributorEnabled",
               builder.build());
        assertThat(contributorEnabled.block()).isEqualTo("true");
    }
}
