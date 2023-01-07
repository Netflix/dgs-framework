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

import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration;
import com.netflix.graphql.dgs.example.context.MyContextBuilder;
import com.netflix.graphql.dgs.example.datafetcher.HelloDataFetcher;
import com.netflix.graphql.dgs.example.shared.context.ExampleGraphQLContextContributor;
import com.netflix.graphql.dgs.example.shared.dataLoader.ExampleLoaderWithContext;
import com.netflix.graphql.dgs.example.shared.dataLoader.ExampleLoaderWithGraphQLContext;
import com.netflix.graphql.dgs.example.shared.instrumentation.ExampleInstrumentationDependingOnContextContributor;
import com.netflix.graphql.dgs.example.shared.datafetcher.MovieDataFetcher;
import com.netflix.graphql.dgs.pagination.DgsPaginationAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static com.netflix.graphql.dgs.example.shared.context.ExampleGraphQLContextContributor.CONTEXT_CONTRIBUTOR_HEADER_NAME;
import static com.netflix.graphql.dgs.example.shared.context.ExampleGraphQLContextContributor.CONTEXT_CONTRIBUTOR_HEADER_VALUE;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {HelloDataFetcher.class, MovieDataFetcher.class, MyContextBuilder.class, ExampleGraphQLContextContributor.class, ExampleInstrumentationDependingOnContextContributor.class, DgsAutoConfiguration.class, DgsPaginationAutoConfiguration.class, ExampleLoaderWithContext.class, ExampleLoaderWithGraphQLContext.class})
public class GraphQLContextContributorTest {

    @Autowired
    DgsQueryExecutor queryExecutor;

    @Test
    void moviesExtensionShouldHaveContributedEnabledExtension() {
        final MockHttpServletRequest mockServletRequest = new MockHttpServletRequest();
        mockServletRequest.addHeader(CONTEXT_CONTRIBUTOR_HEADER_NAME, CONTEXT_CONTRIBUTOR_HEADER_VALUE);
        ServletWebRequest servletWebRequest = new ServletWebRequest(mockServletRequest);
        String contributorEnabled = queryExecutor.executeAndExtractJsonPath("{ movies { director } }", "extensions.contributorEnabled", servletWebRequest);
        assertThat(contributorEnabled).isEqualTo("true");
    }

    @Test
    void withDataloaderContext() {
        String message = queryExecutor.executeAndExtractJsonPath("{withDataLoaderContext}", "data.withDataLoaderContext");
        assertThat(message).isEqualTo("Custom state! A");
    }

    @Test
    void withDataloaderGraphQLContext() {
        final MockHttpServletRequest mockServletRequest = new MockHttpServletRequest();
        mockServletRequest.addHeader(CONTEXT_CONTRIBUTOR_HEADER_NAME, CONTEXT_CONTRIBUTOR_HEADER_VALUE);
        ServletWebRequest servletWebRequest = new ServletWebRequest(mockServletRequest);
        String contributorEnabled = queryExecutor.executeAndExtractJsonPath("{ withDataLoaderGraphQLContext }", "data.withDataLoaderGraphQLContext", servletWebRequest);
        assertThat(contributorEnabled).isEqualTo("true");
    }
}