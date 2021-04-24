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

import com.netflix.graphql.dgs.*
import com.netflix.graphql.dgs.autoconfigure.DgsAutoConfiguration
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.reactive.DgsReactiveCustomContextBuilderWithRequest
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.TypeName
import graphql.schema.idl.TypeDefinitionRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.core.publisher.Mono

@AutoConfigureWebTestClient
@SpringBootTest(classes = [DgsWebFluxAutoConfiguration::class, DgsAutoConfiguration::class, WebRequestTestWithCustomContext.ExampleImplementation::class, WebRequestTestWithCustomContext.MyContextBuilder::class])
class WebRequestTestWithCustomContext {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `A simple request should execute correctly`() {
        val exchange = webTestClient.post().uri("/graphql").bodyValue(
            """
            {"query": "{hello}"}
            """.trimIndent()
        ).exchange().expectBody().jsonPath("data.hello").isEqualTo("Hello, DGS")
    }

    @Test
    fun `Reactive custom context should be available`() {
        val exchange = webTestClient.post().uri("/graphql").header("myheader", "DGS").bodyValue(
            """
            {"query": "{withContext}"}
            """.trimIndent()
        ).exchange().expectBody().jsonPath("data.withContext").isEqualTo("DGS")
    }

    @Test
    fun `@RequestHeader should receive HTTP header`() {
        val exchange = webTestClient.post().uri("/graphql").header("myheader", "DGS").bodyValue(
            """
            {"query": "{withHeader}"}
            """.trimIndent()
        ).exchange().expectBody().jsonPath("data.withHeader").isEqualTo("DGS")
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
                    ).fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("withContext")
                            .type(TypeName("String"))
                            .build()
                    ).fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("withHeader")
                            .type(TypeName("String"))
                            .build()
                    )

                    .build()
            newRegistry.add(query)

            return newRegistry
        }

        @DgsQuery
        fun hello(): String {
            return "Hello, DGS"
        }

        @DgsQuery
        fun withContext(dgsDataFetchingEnvironment: DgsDataFetchingEnvironment): String {
            return DgsContext.getCustomContext(dgsDataFetchingEnvironment)
        }

        @DgsQuery
        fun withHeader(@RequestHeader myheader: String): String {
            return myheader
        }
    }

    @Component
    class MyContextBuilder : DgsReactiveCustomContextBuilderWithRequest<String> {
        override fun build(
            extensions: Map<String, Any>?,
            headers: HttpHeaders?,
            serverRequest: ServerRequest?
        ): Mono<String> {
            return Mono.just(serverRequest?.headers()?.firstHeader("myheader") ?: "")
        }
    }
}
