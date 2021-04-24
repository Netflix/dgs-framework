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

import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.exceptions.QueryException;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;


@ExampleSpringBootTest
class HelloDataFetcherTest {

    @Autowired
    DgsQueryExecutor queryExecutor;

    @Test
    void helloShouldIncludeName() {
        String message = queryExecutor.executeAndExtractJsonPath("{ hello(name: \"DGS\")}", "data.hello");
        assertThat(message).isEqualTo("hello, DGS!");
    }

    @Test
    void helloShouldIncludeNameWithVariables() {
        String message = queryExecutor.executeAndExtractJsonPath("query Hello($name: String) { hello(name: $name)}", "data.hello", Maps.newHashMap("name", "DGS"));
        assertThat(message).isEqualTo("hello, DGS!");
    }

    @Test
    void helloShouldWorkWithoutName() {
        String message = queryExecutor.executeAndExtractJsonPath("{hello}", "data.hello");
        assertThat(message).isEqualTo("hello, Stranger!");
    }

    @Test
    void getQueryWithInspectError() {
        try {
            queryExecutor.executeAndExtractJsonPath("{greeting}", "data.greeting");
            fail("Exception should have been thrown");
        } catch (QueryException ex) {
            assertThat(ex.getMessage()).contains("Validation error of type FieldUndefined: Field 'greeting' in type 'Query' is undefined @ 'greeting'");
            assertThat(ex.getErrors().size()).isEqualTo(1);
        }
    }

    @Test
    void getQueryWithGraphQlException() {
        try {
            queryExecutor.executeAndExtractJsonPath("{withGraphqlException}", "data.greeting");
            fail("Exception should have been thrown");
        } catch (QueryException ex) {
            assertThat(ex.getErrors().get(0).getMessage()).isEqualTo("graphql.GraphQLException: that's not going to work!");
            assertThat(ex.getErrors().size()).isEqualTo(1);
        }
    }

    @Test
    void getQueryWithRuntimeException() {
        try {
            queryExecutor.executeAndExtractJsonPath("{withRuntimeException}", "data.greeting");
            fail("Exception should have been thrown");
        } catch (QueryException ex) {
            assertThat(ex.getErrors().get(0).getMessage()).isEqualTo("java.lang.RuntimeException: That's broken!");
            assertThat(ex.getErrors().size()).isEqualTo(1);
        }
    }

    @Test
    void getQueryWithMultipleExceptions() {
        try {
            queryExecutor.executeAndExtractJsonPath("{withRuntimeException, withGraphqlException}", "data.greeting");
            fail("Exception should have been thrown");
        } catch (QueryException ex) {
            assertThat(ex.getErrors().get(0).getMessage()).isEqualTo("java.lang.RuntimeException: That's broken!");
            assertThat(ex.getErrors().get(1).getMessage()).isEqualTo("graphql.GraphQLException: that's not going to work!");
            assertThat(ex.getMessage()).isEqualTo("java.lang.RuntimeException: That's broken!, graphql.GraphQLException: that's not going to work!");
            assertThat(ex.getErrors().size()).isEqualTo(2);
        }
    }
}