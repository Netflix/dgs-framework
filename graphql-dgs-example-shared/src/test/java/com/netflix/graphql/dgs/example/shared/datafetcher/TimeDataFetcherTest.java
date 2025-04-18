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

package com.netflix.graphql.dgs.example.shared.datafetcher;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.example.shared.ExampleSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;


@ExampleSpringBootTest
class TimeDataFetcherTest {

    @Autowired
    DgsQueryExecutor queryExecutor;

    @Test
    void returnCurrentTime() {
        String message = queryExecutor.executeAndExtractJsonPath("{ now }", "data.now");
        assertThat(message).isNotNull();
    }

    @Test
    void acceptTime() {
        LocalTime aTime = LocalTime.parse("12:01:00");
        Boolean result = queryExecutor.executeAndExtractJsonPath("{ schedule(time:\""+aTime.format(DateTimeFormatter.ISO_LOCAL_TIME)+"\") }", "data.schedule");
        assertThat(result).isTrue();
    }
}
