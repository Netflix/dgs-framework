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
import com.netflix.graphql.dgs.example.context.MyContextContributor;
import com.netflix.graphql.dgs.example.instrumentation.ExampleInstrumentation;
import com.netflix.graphql.dgs.example.shared.datafetcher.MovieDataFetcher;
import com.netflix.graphql.dgs.pagination.DgsPaginationAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;

import static com.netflix.graphql.dgs.example.context.MyContextContributor.CONTEXT_CONTRIBUTOR_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {MovieDataFetcher.class, MyContextContributor.class, ExampleInstrumentation.class, DgsAutoConfiguration.class, DgsPaginationAutoConfiguration.class})
public class MovieDataFetcherContextContributorTest {

    @Autowired
    DgsQueryExecutor queryExecutor;

    @Test
    void moviesExtensionShouldHaveContributedEnabledExtension() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(CONTEXT_CONTRIBUTOR_HEADER, "enabled");
        String contributorEnabled = queryExecutor.executeAndExtractJsonPath("{ movies { director } }", "extensions.contributorEnabled", headers);
        assertThat(contributorEnabled).isEqualTo("true");
    }
}
