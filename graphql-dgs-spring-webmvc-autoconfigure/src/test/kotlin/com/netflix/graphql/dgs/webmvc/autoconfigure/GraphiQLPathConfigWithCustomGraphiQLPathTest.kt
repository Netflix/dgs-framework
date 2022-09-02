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

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["dgs.graphql.graphiql.path=/magic/things"]
)
class GraphiQLPathConfigWithCustomGraphiQLPathTest(@Autowired val restTemplate: TestRestTemplate) {

    @Test
    fun customGraphiQLPath() {
        val entity = restTemplate.getForEntity(
            "/magic/things",
            String::class.java
        )
        assertTrue(entity.statusCode.is2xxSuccessful)
        Assertions.assertThat(entity.body).isNotNull.contains("fetch('./graphql'")
    }
}
