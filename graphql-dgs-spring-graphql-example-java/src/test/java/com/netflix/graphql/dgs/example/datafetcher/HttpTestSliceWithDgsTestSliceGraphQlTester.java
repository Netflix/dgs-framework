/*
 * Copyright 2024 Netflix, Inc.
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

import com.netflix.graphql.dgs.autoconfig.DgsExtendedScalarsAutoConfiguration;
import com.netflix.graphql.dgs.pagination.DgsPaginationAutoConfiguration;
import com.netflix.graphql.dgs.scalars.UploadScalar;
import com.netflix.graphql.dgs.springgraphql.autoconfig.DgsSpringGraphQLAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.graphql.servlet.GraphQlWebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

@SpringBootTest(classes={HelloDataFetcher.class,
        DgsSpringGraphQLAutoConfiguration.class,
        com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration.class,
        com.netflix.graphql.dgs.springgraphql.autoconfig.DgsSpringGraphQLSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration.class,
        UploadScalar.class,
        DgsExtendedScalarsAutoConfiguration.class,
        DgsPaginationAutoConfiguration.class,
        GraphQlWebMvcAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        WebFluxAutoConfiguration.class
},
    properties = {"debug=true"})
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
public class HttpTestSliceWithDgsTestSliceGraphQlTester {

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
