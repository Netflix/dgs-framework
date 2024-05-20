/*
 * Copyright 2023 Netflix, Inc.
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

package com.netflix.graphql.dgs.example.reactive;

import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class ReactiveDataFetchersTest {

    @Autowired
    private DgsReactiveQueryExecutor queryExecutor;

    @Test
    public void testMonoQuery() {
        var result = queryExecutor.executeAndExtractJsonPath("{ mono }", "data.mono")
                .block();

        assertThat(result).isEqualTo("hello mono");
    }

    @Test
    public void testHello() {
        var result = queryExecutor.executeAndExtractJsonPath("{ hello }", "data.hello")
                .block();

        assertThat(result).isEqualTo("hello, Stranger!");
    }

    @Test
    public void testGreetings() {
        var result = queryExecutor.executeAndExtractJsonPath("{ greetings(name: \"DGS\") }", "data.greetings")
                .block();

        assertThat(result).isEqualTo("Greetings, DGS!");
    }

    @Test
    public void testGreetingsMono() {
        var result = queryExecutor.executeAndExtractJsonPath("{ greetingsMono(name: \"DGS\") }", "data.greetingsMono")
                .block();

        assertThat(result).isEqualTo("Greetings, DGS!");
    }

    @Test
    void webRequest() {
        MockServerRequest mockServerRequest = MockServerRequest.builder()
                .exchange(MockServerWebExchange.builder(MockServerHttpRequest.get("/graphql").cookie(new HttpCookie("mydgscookie", "DGS cookies are yummy")).build()).build())
                .cookie(new HttpCookie("mydgscookie", "DGS cookies are yummy")).build();
        var result = queryExecutor.execute("{ withCookie }", Collections.emptyMap(), Collections.emptyMap(), null, "", mockServerRequest).block();
        assertThat(result.isDataPresent()).isTrue();
        Map<String, Object> data = result.getData();
        assertThat(data.get("withCookie")).isEqualTo("DGS cookies are yummy");
    }
}
