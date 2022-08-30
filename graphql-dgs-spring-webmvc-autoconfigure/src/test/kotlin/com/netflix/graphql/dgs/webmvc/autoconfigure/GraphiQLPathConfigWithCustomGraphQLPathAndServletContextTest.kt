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

package com.netflix.graphql.dgs.webmvc.autoconfigure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestInterceptor

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["dgs.graphql.path=/zuzu", "server.servlet.context-path=/foo"]
)
class GraphiQLPathConfigWithCustomGraphQLPathAndServletContextTest(
    @Autowired private val testRestTemplate: TestRestTemplate
) {

    @BeforeEach
    fun setRestTemplateHeaders() {
        testRestTemplate.restTemplate.interceptors.add(
            ClientHttpRequestInterceptor { request, body, execution ->
                request.headers.contentType = MediaType.APPLICATION_JSON
                return@ClientHttpRequestInterceptor execution.execute(request, body)
            }
        )
    }

    @Test
    fun customGraphQLPathAndCustomServletContext() {
        /*
        The graphiql endpoint returns the client side javascript and we don't actually execute it in order to verify
        the validity of the "fetch" uri. In order to verify the "fetch" uri we have to prove some conditions
        to ensure we are testing this properly.

        Fact1: The server has been configured w/ a context path ending w/ "/foo"
        Fact2: The graphql controller is NOT available at "/graphql"
        Fact3: The graphql controller IS available at "/foo/graphql"
        Fact4: The graphiql javascript has its fetch replaced w/ "foo/graphql"
        */

        val rootUri = testRestTemplate.rootUri
        // server has been configured with context path
        assertThat(rootUri).endsWith("/foo")

        // graphql not available without context path in uri
        val absPathWithoutContextPath = rootUri.substring(0, rootUri.length - "/foo".length) + "/zuzu"
        var graphqlResponse = testRestTemplate.getForEntity(
            absPathWithoutContextPath,
            String::class.java
        )
        assertThat(graphqlResponse.statusCodeValue).isEqualTo(HttpStatus.NOT_FOUND.value())

        // graphql is available with context path in uri (400 expected as we don't sent proper request)
        val absPathWithContextPath = "$rootUri/zuzu"
        graphqlResponse = testRestTemplate.getForEntity(
            absPathWithContextPath,
            String::class.java
        )
        assertThat(graphqlResponse.statusCodeValue).isEqualTo(HttpStatus.BAD_REQUEST.value())

        val graphiqlResponse = testRestTemplate.getForEntity(
            "/graphiql",
            String::class.java
        )
        assertTrue(graphiqlResponse.statusCode.is2xxSuccessful)
        assertThat(graphiqlResponse.body).isNotNull.contains("fetch('./foo/zuzu'")
    }
}
