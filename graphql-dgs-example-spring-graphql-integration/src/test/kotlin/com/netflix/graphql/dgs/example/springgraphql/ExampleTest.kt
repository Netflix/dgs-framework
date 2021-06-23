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

package com.netflix.graphql.dgs.example.springgraphql

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.boot.test.tester.AutoConfigureGraphQlTester
import org.springframework.graphql.test.tester.GraphQlTester


@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureGraphQlTester
class ExampleTest {

    @Autowired
    private lateinit var graphQlTester: GraphQlTester

    @Test
    fun jsonPath() {
        val query = """
            {
                hello
            }
        """.trimIndent()

        val result = graphQlTester.query(query)
            .execute()
            .path("hello")
            .entity(String::class.java).get()

        assertThat(result).isEqualTo("hello, Stranger!")
    }
}