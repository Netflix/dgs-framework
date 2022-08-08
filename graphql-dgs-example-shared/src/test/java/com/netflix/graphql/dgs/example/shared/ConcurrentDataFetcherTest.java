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

package com.netflix.graphql.dgs.example.shared;

import com.jayway.jsonpath.DocumentContext;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Disabled("The need of this test is questionable, since the concurrent execution of data fetchers is left to the graphql-java engine.")
@ExampleSpringBootTest
class ConcurrentDataFetcherTest {

    @Autowired
    DgsQueryExecutor queryExecutor;

    @Test
    void concurrent() {

        DocumentContext documentContext = queryExecutor.executeAndGetDocumentContext("{concurrent1, concurrent2}");
        int ts1 = documentContext.read("data.concurrent1");
        int ts2 = documentContext.read("data.concurrent2");
        // you can't assume the order of execution of unrelated fields, in this case data.concurrent2 can be fetched
        // before data.concurrent1 or vice versa.
        assertThat(ts1).isCloseTo(ts2, Percentage.withPercentage(10.0));
    }
}