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
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData
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
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import java.util.*
import javax.servlet.http.Cookie

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
    fun `@RequestHeader should support defaultValue`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ usingHeader }" }""")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"usingHeader": "default header"}}"""))
    }

    @Test
    fun `@RequestHeader should throw an exception when not provided but required and no default is set`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ usingRequiredHeader }" }""")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"errors":[{"message":"com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException: Required header 'myheader' was not provided","locations":[],"path":["usingRequiredHeader"],"extensions":{"errorType":"INTERNAL"}}],"data":{"usingRequiredHeader":null}}"""))
    }

    @Test
    fun `@RequestHeader should use null if not required and not provided`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ usingOptionalHeader }" }""")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"usingOptionalHeader": "default header from datafetcher"}}"""))
    }

    @Test
    fun `@RequestHeader should support Optional for not provided values`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ usingOptionalHeaderAsOptionalType }" }""")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"usingOptionalHeaderAsOptionalType": "default header from Optional"}}"""))
    }

    @Test
    fun `@RequestHeader should support Optional for provided values`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ usingOptionalHeaderAsOptionalType }" }""")
                .header("myheader", "hello")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"usingOptionalHeaderAsOptionalType": "hello"}}"""))
    }

    @Test
    fun `@RequestParam should be available`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ usingParam }" }""")
                .param("myParam", "paramValue")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"usingParam": "paramValue"}}"""))
    }

    @Test
    fun `@RequestParam should properly handle multiple param values`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ usingParam }" }""")
                .param("myParam", "paramValue")
                .param("myParam", "paramValue2")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"usingParam": "paramValue, paramValue2"}}"""))
    }

    @Test
    fun `@RequestParam should use default when no parameter was provided`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ usingParam }" }""")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"usingParam": "default parameter"}}"""))
    }

    @Test
    fun `@RequestParam should throw exception when no parameter was provided and no default is set`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ usingParamRequired }" }""")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"errors":[{"message":"com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException: Required request parameter 'myParam' was not provided","locations":[],"path":["usingParamRequired"],"extensions":{"errorType":"INTERNAL"}}],"data":{"usingParamRequired":null}}"""))
    }

    @Test
    fun `@RequestParam should use null when not required and not provided`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ usingOptionalParam }" }""")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"usingOptionalParam": "default from datafetcher"}}"""))
    }

    @Test
    fun `@RequestParam should support Optional parameters with non required null values`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ usingOptionalParamAsOptionalType }" }""")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"usingOptionalParamAsOptionalType": "default param from Optional"}}"""))
    }

    @Test
    fun `@RequestParam should support Optional parameters`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ usingOptionalParamAsOptionalType }" }""")
                .param("myParam", "hello")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"usingOptionalParamAsOptionalType": "hello"}}"""))
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

    @Test
    fun `@CookieValue should give access to cookie`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ withCookie }" }""")
                .cookie(Cookie("myCookie", "cookiehello"))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"withCookie": "cookiehello"}}"""))
    }

    @Test
    fun `@CookieValue should allow Optional type`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ withOptionalCookie }" }""")
                .cookie(Cookie("myCookie", "cookiehello"))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"withOptionalCookie": "cookiehello"}}"""))
    }

    @Test
    fun `@CookieValue should allow empty Optional type`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ withEmptyOptionalCookie }" }""")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"withEmptyOptionalCookie": "emptycookie"}}"""))
    }

    @Test
    fun `@CookieValue should allow null when not required`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ withEmptyCookie }" }""")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"withEmptyCookie": "emptycookie"}}"""))
    }

    @Test
    fun `@CookieValue should throw exception when required but not set`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ withRequiredCookie }" }""")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"errors":[{"message":"com.netflix.graphql.dgs.exceptions.DgsMissingCookieException: Required cookie 'myCookie' was not provided","locations":[],"path":["withRequiredCookie"],"extensions":{"errorType":"INTERNAL"}}],"data":{"withRequiredCookie":null}}"""))
    }

    @Test
    fun `@CookieValue should support default value`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/graphql")
                .content("""{"query": "{ withDefaultCookie }" }""")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().json("""{"data":{"withDefaultCookie": "defaultvalue"}}"""))
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
                    ).fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("usingRequiredHeader")
                            .type(TypeName("String"))
                            .build()
                    ).fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("usingOptionalHeader")
                            .type(TypeName("String"))
                            .build()
                    ).fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("usingOptionalHeaderAsOptionalType")
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
                    .fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("usingParam")
                            .type(TypeName("String"))
                            .build()
                    ).fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("usingParamRequired")
                            .type(TypeName("String"))
                            .build()
                    ).fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("usingOptionalParam")
                            .type(TypeName("String"))
                            .build()
                    ).fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("usingOptionalParamAsOptionalType")
                            .type(TypeName("String"))
                            .build()
                    ).fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("withCookie")
                            .type(TypeName("String"))
                            .build()
                    ).fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("withOptionalCookie")
                            .type(TypeName("String"))
                            .build()
                    ).fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("withEmptyOptionalCookie")
                            .type(TypeName("String"))
                            .build()
                    ).fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("withEmptyCookie")
                            .type(TypeName("String"))
                            .build()
                    ).fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("withRequiredCookie")
                            .type(TypeName("String"))
                            .build()
                    ).fieldDefinition(
                        FieldDefinition
                            .newFieldDefinition()
                            .name("withDefaultCookie")
                            .type(TypeName("String"))
                            .build()
                    )
                    .build()
            newRegistry.add(query)

            return newRegistry
        }

        @DgsData(parentType = "Query", field = "usingWebRequest")
        fun usingWebRequest(dfe: DgsDataFetchingEnvironment): String {
            return ((DgsContext.getRequestData(dfe) as DgsWebMvcRequestData).webRequest as ServletWebRequest).request.serverName
        }

        @DgsData(parentType = "Query", field = "usingHeader")
        fun usingRequestHeader(
            @RequestHeader(defaultValue = "default header") myheader: String?,
            dataFetchingEnvironment: DgsDataFetchingEnvironment
        ): String {
            return myheader ?: "empty"
        }

        @DgsData(parentType = "Query", field = "usingRequiredHeader")
        fun usingRequiredRequestHeader(
            @RequestHeader(required = true) myheader: String,
            dataFetchingEnvironment: DgsDataFetchingEnvironment
        ): String {
            return myheader
        }

        @DgsData(parentType = "Query", field = "usingOptionalHeader")
        fun usingOptionalRequestHeader(
            @RequestHeader(required = false) myheader: String?,
            dataFetchingEnvironment: DgsDataFetchingEnvironment
        ): String {
            return myheader ?: "default header from datafetcher"
        }

        @DgsData(parentType = "Query", field = "usingOptionalHeaderAsOptionalType")
        fun usingOptionalRequestHeader(
            @RequestHeader(required = false) myheader: Optional<String>,
            dataFetchingEnvironment: DgsDataFetchingEnvironment
        ): String {
            return myheader.orElse("default header from Optional")
        }

        @DgsData(parentType = "Query", field = "usingContextWithRequest")
        fun usingContextWithRequest(dataFetchingEnvironment: DgsDataFetchingEnvironment): String {
            val customContext: TestContext = DgsContext.getCustomContext(dataFetchingEnvironment)
            return customContext.myheader
        }

        @DgsData(parentType = "Query", field = "usingParam")
        fun usingRequestParam(
            @RequestParam(defaultValue = "default parameter") myParam: String,
            dataFetchingEnvironment: DgsDataFetchingEnvironment
        ): String {
            return myParam
        }

        @DgsData(parentType = "Query", field = "usingParamRequired")
        fun usingRequestParamRequired(
            @RequestParam(required = true) myParam: String,
            dataFetchingEnvironment: DgsDataFetchingEnvironment
        ): String {
            return myParam
        }

        @DgsData(parentType = "Query", field = "usingOptionalParam")
        fun usingOptionalRequestParam(
            @RequestParam(required = false) myParam: String?,
            dataFetchingEnvironment: DgsDataFetchingEnvironment
        ): String {
            return myParam ?: "default from datafetcher"
        }

        @DgsData(parentType = "Query", field = "usingOptionalParamAsOptionalType")
        fun usingOptionalParamAsOptionalType(
            @RequestParam(required = false) myParam: Optional<String>,
            dataFetchingEnvironment: DgsDataFetchingEnvironment
        ): String {
            return myParam.orElse("default param from Optional")
        }

        @DgsData(parentType = "Query", field = "withCookie")
        fun usingCookie(
            @CookieValue myCookie: String,
        ): String {
            return myCookie
        }

        @DgsData(parentType = "Query", field = "withOptionalCookie")
        fun usingOptionalCookie(
            @CookieValue myCookie: Optional<String>,
        ): String {
            return myCookie.get()
        }

        @DgsData(parentType = "Query", field = "withEmptyOptionalCookie")
        fun usingEmptyOptionalCookie(
            @CookieValue(required = false) myCookie: Optional<String>,
        ): String {
            return myCookie.orElse("emptycookie")
        }

        @DgsData(parentType = "Query", field = "withEmptyCookie")
        fun usingEmptyOptionalCookie(
            @CookieValue(required = false) myCookie: String?,
        ): String {
            return myCookie ?: "emptycookie"
        }

        @DgsData(parentType = "Query", field = "withRequiredCookie")
        fun usingRequiredCookie(
            @CookieValue(required = true) myCookie: String,
        ): String {
            return myCookie
        }

        @DgsData(parentType = "Query", field = "withDefaultCookie")
        fun usingCookieWithDefault(
            @CookieValue(defaultValue = "defaultvalue") myCookie: String,
        ): String {
            return myCookie
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
