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

import com.netflix.graphql.dgs.*;
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration;
import com.netflix.graphql.dgs.autoconfig.DgsExtendedScalarsAutoConfiguration;
import com.netflix.graphql.dgs.example.datafetcher.HelloDataFetcher;
import com.netflix.graphql.dgs.example.shared.dataLoader.MessageDataLoaderWithDispatchPredicate;
import com.netflix.graphql.dgs.example.shared.datafetcher.*;
import com.netflix.graphql.dgs.pagination.DgsPaginationAutoConfiguration;
import com.netflix.graphql.dgs.webmvc.autoconfigure.DgsWebMvcAutoConfiguration;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {HelloDataFetcher.class, MovieDataFetcher.class, ConcurrentDataFetcher.class, RequestHeadersDataFetcher.class, DgsExtendedScalarsAutoConfiguration.class, DgsAutoConfiguration.class, DgsPaginationAutoConfiguration.class, DgsWebMvcAutoConfiguration.class})
class RequestHeaderDataFetcherTest {

    @Autowired
    DgsQueryExecutor queryExecutor;

    @Test
    void withHeaders() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("demo-header", "demo-header-value");

        String message = queryExecutor.executeAndExtractJsonPath("{headers}", "data.headers", new ServletWebRequest(servletRequest));
        assertThat(message).isEqualTo("demo-header-value");
    }

    @Test
    void withHeadersAndNoRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("demo-header", "demo-header-value");

        String message = queryExecutor.executeAndExtractJsonPath("{headers}", "data.headers", headers);
        assertThat(message).isEqualTo("demo-header-value");
    }
}