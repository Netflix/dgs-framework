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

package com.netflix.graphql.dgs.webflux.autoconfiguration

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.autoconfigure.DgsAutoConfiguration
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.TypeName
import graphql.schema.idl.TypeDefinitionRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient

@AutoConfigureWebTestClient
@SpringBootTest(classes = [DgsWebFluxAutoConfiguration::class, DgsAutoConfiguration::class, WebRequestTestWithCustomEndpoint.ExampleImplementation::class], properties = ["dgs.graphql.path=/api"])
class WebRequestTestWithCustomEndpoint {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `A simple request should execute correctly when no custom context builder is available`() {
        val exchange = webTestClient.post().uri("/api").bodyValue(
            """
            {"query": "{hello}"}
            """.trimIndent()
        ).exchange().expectBody().jsonPath("data.hello").isEqualTo("Hello, DGS")
    }

    @DgsComponent
    class ExampleImplementation {

        @DgsTypeDefinitionRegistry
        fun typeDefinitionRegistry(): TypeDefinitionRegistry {
            val newRegistry = TypeDefinitionRegistry()

            val query =
                ObjectTypeDefinition
                    .newObjectTypeDefinition()
                    .name("Query")
                    .fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("hello")
                            .type(TypeName("String"))
                            .build()
                    ).build()
            newRegistry.add(query)

            return newRegistry
        }

        @DgsQuery
        fun hello(): String {
            return "Hello, DGS"
        }
    }
}
