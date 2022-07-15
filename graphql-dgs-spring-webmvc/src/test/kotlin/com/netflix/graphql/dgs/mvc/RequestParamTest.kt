/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.graphql.dgs.mvc

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import com.netflix.graphql.dgs.mvc.internal.method.HandlerMethodArgumentResolverAdapter
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver
import java.util.*

class RequestParamTest {

    private val applicationContext = GenericApplicationContext()
    private val provider: DgsSchemaProvider by lazy {
        DgsSchemaProvider(
            applicationContext = applicationContext,
            federationResolver = Optional.empty(),
            existingTypeDefinitionRegistry = Optional.empty(),
            methodDataFetcherFactory = MethodDataFetcherFactory(
                listOf(
                    HandlerMethodArgumentResolverAdapter(RequestParamMapMethodArgumentResolver()),
                    HandlerMethodArgumentResolverAdapter(RequestParamMethodArgumentResolver(false))
                )
            )
        )
    }

    @Test
    fun `A @RequestParam argument should be supported`() {
        @DgsComponent
        class Fetcher {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@RequestParam("message") input: String): String {
                return input
            }

            @DgsTypeDefinitionRegistry
            fun typeDefinitionRegistry(): TypeDefinitionRegistry {
                return SchemaParser().parse("type Query { hello(name: String): String }")
            }
        }

        applicationContext.registerBean("helloFetcher", Fetcher::class.java, *emptyArray())
        applicationContext.refresh()

        val schema = provider.schema()

        val build = GraphQL.newGraphQL(schema).build()

        val request = ServletWebRequest(
            MockHttpServletRequest().apply {
                addParameter("message", "My param")
            }
        )

        val executionResult = build.execute(
            ExecutionInput.newExecutionInput("""{hello}""")
                .graphQLContext(DgsContext(null, DgsWebMvcRequestData(emptyMap(), request)))
        )
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("My param", data["hello"])
    }

    @Test
    fun `A @RequestParam argument with name specified in 'name' argument should be supported`() {
        @DgsComponent
        class Fetcher {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@RequestParam(name = "message") input: String): String {
                return input
            }

            @DgsTypeDefinitionRegistry
            fun typeDefinitionRegistry(): TypeDefinitionRegistry {
                return SchemaParser().parse("type Query { hello(name: String): String }")
            }
        }

        applicationContext.registerBean("helloFetcher", Fetcher::class.java, *emptyArray())
        applicationContext.refresh()

        val schema = provider.schema()

        val build = GraphQL.newGraphQL(schema).build()

        val request = ServletWebRequest(
            MockHttpServletRequest().apply {
                addParameter("message", "My param")
            }
        )

        val executionResult = build.execute(
            ExecutionInput.newExecutionInput("""{hello}""")
                .graphQLContext(DgsContext(null, DgsWebMvcRequestData(emptyMap(), request)))
        )
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("My param", data["hello"])
    }

    @Test
    fun `A @RequestParam argument with no explicit name should use parameter name`() {
        @DgsComponent
        class Fetcher {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@RequestParam message: String): String {
                return message
            }

            @DgsTypeDefinitionRegistry
            fun typeDefinitionRegistry(): TypeDefinitionRegistry {
                return SchemaParser().parse("type Query { hello(name: String): String }")
            }
        }

        applicationContext.registerBean("helloFetcher", Fetcher::class.java, *emptyArray())
        applicationContext.refresh()

        val schema = provider.schema()

        val build = GraphQL.newGraphQL(schema).build()

        val request = ServletWebRequest(
            MockHttpServletRequest().apply {
                addParameter("message", "My param")
            }
        )

        val executionResult = build.execute(
            ExecutionInput.newExecutionInput("""{hello}""")
                .graphQLContext(DgsContext(null, DgsWebMvcRequestData(emptyMap(), request)))
        )
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("My param", data["hello"])
    }
}
