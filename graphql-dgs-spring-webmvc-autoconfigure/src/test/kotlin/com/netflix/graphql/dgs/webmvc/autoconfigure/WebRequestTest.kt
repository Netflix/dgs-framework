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

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.context.DgsCustomContextBuilderWithRequest
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.TypeName
import graphql.schema.idl.TypeDefinitionRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest

@SpringBootTest(
    classes = [DgsWebMvcAutoConfiguration::class, DgsAutoConfiguration::class, WebRequestTest.ExampleImplementation::class, WebRequestTest.TestCustomContextBuilder::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
class WebRequestTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `WebRequest should be available on DgsContext`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ usingWebRequest }" }""")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"usingWebRequest": "localhost"}}"""))
    }

    @Test
    fun `@RequestHeader should be available`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ usingHeader }" }""")
                .header("myheader", "hello")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"usingHeader": "hello"}}"""))
    }

    @Test
    fun `Custom context builder should have access to headers`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ usingContextWithRequest }" }""")
                .header("myheader", "hello")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"usingContextWithRequest": "hello"}}"""))
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
                            .name("usingWebRequest")
                            .type(TypeName("String"))
                            .build()
                    )
                    .fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("usingHeader")
                            .type(TypeName("String"))
                            .build()
                    )
                    .fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("usingContextWithRequest")
                            .type(TypeName("String"))
                            .build()
                    )
                    .build()
            newRegistry.add(query)

            return newRegistry
        }

        @DgsData(parentType = "Query", field = "usingWebRequest")
        fun usingWebRequest(dfe: DgsDataFetchingEnvironment): String {
            return (DgsContext.getRequestData(dfe)?.webRequest as ServletWebRequest).request.serverName
        }

        @DgsData(parentType = "Query", field = "usingHeader")
        fun usingRequestHeader(
            @RequestHeader myheader: String?,
            dataFetchingEnvironment: DgsDataFetchingEnvironment
        ): String {
            return myheader ?: "empty"
        }

        @DgsData(parentType = "Query", field = "usingContextWithRequest")
        fun usingContextWithRequest(dataFetchingEnvironment: DgsDataFetchingEnvironment): String {
            val customContext: TestContext = DgsContext.getCustomContext(dataFetchingEnvironment)
            return customContext.myheader
        }
    }

    @Component
    class TestCustomContextBuilder : DgsCustomContextBuilderWithRequest<TestContext> {
        override fun build(extensions: Map<String, Any>?, headers: HttpHeaders?, webRequest: WebRequest?): TestContext {
            return TestContext(headers?.getFirst("myheader") ?: "not set")
        }
    }

    data class TestContext(val myheader: String)
}
