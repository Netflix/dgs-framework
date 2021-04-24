/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.graphql.dgs.example;

import com.jayway.jsonpath.DocumentContext;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExampleSpringBootTest
class ConcurrentDataFetcherTest {

    @Autowired
    DgsQueryExecutor queryExecutor;

    @Test
    void concurrent() {

        DocumentContext documentContext = queryExecutor.executeAndGetDocumentContext("{concurrent1, concurrent2}");
        int ts1 = documentContext.read("data.concurrent1");
        int ts2 = documentContext.read("data.concurrent2");

        assertTrue(ts1 > ts2);
    }
}