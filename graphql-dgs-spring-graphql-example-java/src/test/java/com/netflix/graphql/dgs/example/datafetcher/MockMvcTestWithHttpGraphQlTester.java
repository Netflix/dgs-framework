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

package com.netflix.graphql.dgs.example.datafetcher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;


@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
public class MockMvcTestWithHttpGraphQlTester {

    @Autowired
    private HttpGraphQlTester graphQlTester;

    @Test
    void testSpringDataFetcher() {
        graphQlTester.document("query Greetings($name: String){ greetings(name: $name) }").variable("name", "Spring GraphQL").execute()
                .path("greetings").entity(String.class).isEqualTo("Hello, Spring GraphQL!");
    }

    @Test
    void testDgsDataFetcher() {
        graphQlTester.document("query Hello($name: String){ hello(name: $name) }").variable("name", "DGS").execute()
                .path("hello").entity(String.class).isEqualTo("hello, DGS!");
    }

    @Test
    void withDataLoader() {
        graphQlTester.document("query { messageFromBatchLoader }").execute()
                .path("messageFromBatchLoader").entity(String.class).isEqualTo("hello, a!");
    }
}
