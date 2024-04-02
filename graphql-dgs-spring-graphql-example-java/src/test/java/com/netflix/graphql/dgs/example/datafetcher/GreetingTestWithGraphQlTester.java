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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;

@SpringBootTest
@Disabled
public class GreetingTestWithGraphQlTester {
    @Autowired
    ExecutionGraphQlService service;
    private ExecutionGraphQlServiceTester graphQlTester;
    @BeforeEach
    void setUp() {
        graphQlTester = ExecutionGraphQlServiceTester.create(service);
    }

    @Test
    void testSpringDataFetcher() {
        graphQlTester.document("query Greetings($name: String){ greetings(name: $name) }").variable("name", "Spring GraphQL").execute()
                .path("greetings").entity(String.class).isEqualTo("Hello, Spring GraphQL!");
    }
}
