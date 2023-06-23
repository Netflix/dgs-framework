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

package com.netflix.graphql.dgs.example.datafetcher;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.example.shared.types.ActionMovie;
import com.netflix.graphql.dgs.example.shared.types.Movie;
import graphql.ExecutionResult;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class GreetingTest {
    @Autowired
    DgsQueryExecutor queryExecutor;

    @Test
    void testSpringDataFetcher() {
        var result = queryExecutor.execute("query Greetings($name: String){ greetings(name: $name) }", Map.of("name", "DGS"), Collections.emptyMap(), null, "", null);

        assertThat(result.isDataPresent()).isTrue();
        Map<String, Object> data = result.getData();
        assertThat(data.get("greetings")).isEqualTo("Hello, DGS!");

    }

    @Test
    void testDgsDataFetcher() {
        ExecutionResult result = queryExecutor.execute("query Hello($name: String){ hello(name: $name) }", Map.of("name", "DGS"));
        assertThat(result.isDataPresent()).isTrue();
        Map<String, Object> data = result.getData();
        assertThat(data.get("hello")).isEqualTo("hello, DGS!");
    }

    @Test
    void withDataLoader() {
        ExecutionResult result = queryExecutor.execute("{messageFromBatchLoader}");
        assertThat(result.isDataPresent()).isTrue();
        Map<String, Object> data = result.getData();
        assertThat(data.get("messageFromBatchLoader")).isEqualTo("hello, a!");
    }

    @Test
    void withDataLoaderAsync() {
        ExecutionResult result = queryExecutor.execute("{withDataLoaderContext}");
        assertThat(result.isDataPresent()).isTrue();
        Map<String, Object> data = result.getData();
        assertThat(data.get("withDataLoaderContext")).isEqualTo("Custom state! A");
    }

    @Test
    void headers() {
        var headers = HttpHeaders.writableHttpHeaders(HttpHeaders.EMPTY);
        headers.add("my-header", "DGS rocks!");
        headers.add("referer", "Test");

        var result = queryExecutor.execute("{ headers }", Collections.emptyMap(), Collections.emptyMap(), headers, "", null);
        assertThat(result.isDataPresent()).isTrue();
        Map<String, Object> data = result.getData();
        assertThat(data.get("headers")).isEqualTo("""
                [my-header:"DGS rocks!", referer:"Test"]
                """.trim());
    }

    @Test
    void webRequest() {
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        mockHttpServletRequest.setCookies(new Cookie("mydgscookie", "DGS cookies are yummy"));

        ServletWebRequest servletWebRequest = new ServletWebRequest(mockHttpServletRequest);

        var result = queryExecutor.execute("{ withCookie }", Collections.emptyMap(), Collections.emptyMap(), null, "", servletWebRequest);
        assertThat(result.isDataPresent()).isTrue();
        Map<String, Object> data = result.getData();
        assertThat(data.get("withCookie")).isEqualTo("DGS cookies are yummy");
    }

    @Test
    void jsonPath() {
        String query = "query Hello($name: String){ hello(name: $name) }";
        var result = queryExecutor.executeAndExtractJsonPath(query, "data.hello");
        assertThat(result).isEqualTo("hello, Stranger!");
    }

    @Test
    void jsonPathWithVariables() {
        String query = "query Hello($name: String){ hello(name: $name) }";
        Map<String, Object> variables = Map.of("name", "DGS");
        var result = queryExecutor.executeAndExtractJsonPath(query, "data.hello", variables);
        assertThat(result).isEqualTo("hello, DGS!");
    }

    @Test
    void jsonPathWithHeaders() {
        String query = "{ headers }";
        var headers = HttpHeaders.writableHttpHeaders(HttpHeaders.EMPTY);
        headers.add("my-header", "DGS rocks!");
        headers.add("referer", "Test");

        var result = queryExecutor.executeAndExtractJsonPath(query, "data.headers", headers);
        assertThat(result).isEqualTo("[my-header:\"DGS rocks!\", referer:\"Test\"]");
    }

    @Test
    void jsonPathWithSebRequest() {
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        mockHttpServletRequest.setCookies(new Cookie("mydgscookie", "DGS cookies are yummy"));

        ServletWebRequest servletWebRequest = new ServletWebRequest(mockHttpServletRequest);

        var result = queryExecutor.executeAndExtractJsonPath("{ withCookie }", "data.withCookie", servletWebRequest);
        assertThat(result).isEqualTo("DGS cookies are yummy");
    }

    @Test
    void jsonPathAsObject() {
        Movie movie = queryExecutor.executeAndExtractJsonPathAsObject("{movies { title} }", "data.movies[0]", ActionMovie.class);
        assertThat(movie.getTitle()).isEqualTo("Crouching Tiger");
    }

    @Test
    void jsonPathAsObjectRef() {
        List<ActionMovie> movies = queryExecutor.executeAndExtractJsonPathAsObject("{movies { title} }", "data.movies", new TypeRef<List<ActionMovie>>() {
        });
        assertThat(movies).size().isEqualTo(4);
    }

    @Test
    void documentContext() {
        DocumentContext documentContext = queryExecutor.executeAndGetDocumentContext("{hello}");
        assertThat(documentContext.<String>read("data.hello")).isEqualTo("hello, Stranger!");
    }

    @Test
    void documentContextWithHeaders() {
        DocumentContext documentContext = queryExecutor.executeAndGetDocumentContext("query Hello($name: String){ hello(name: $name) }", Map.of("name", "DGS"));
        assertThat(documentContext.<String>read("data.hello")).isEqualTo("hello, DGS!");
    }
}
