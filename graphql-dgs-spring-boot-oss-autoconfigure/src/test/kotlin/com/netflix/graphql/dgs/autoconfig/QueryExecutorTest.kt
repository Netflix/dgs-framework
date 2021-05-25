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

package com.netflix.graphql.dgs.autoconfig

import com.jayway.jsonpath.PathNotFoundException
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.autoconfig.testcomponents.HelloDataFetcherConfig
import com.netflix.graphql.dgs.exceptions.QueryException
import graphql.ExecutionResult
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.http.HttpHeaders
import org.springframework.util.LinkedMultiValueMap

class QueryExecutorTest {
    private val context = WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(DgsAutoConfiguration::class.java))!!

    @Test
    fun query() {
        context.withUserConfiguration(HelloDataFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.executeAndExtractJsonPath<String>("{ hello }", "data.hello")
            }.isEqualTo("Hello!")
        }
    }

    @Test
    fun queryWithoutHeaderThrowsException() {
        assertThrows(QueryException::class.java) {
            context.withUserConfiguration(HelloDataFetcherConfig::class.java).run { ctx ->
                assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                    it.executeAndGetDocumentContext("{ helloWithHeader }", mapOf(), null)
                }
            }
        }
    }

    @Test
    fun queryWithHeaderNotThrowsException() {
        val headers = LinkedMultiValueMap<String, String>()
        headers.add(HttpHeaders.AUTHORIZATION, "test")
        context.withUserConfiguration(HelloDataFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.executeAndGetDocumentContext("{ helloWithHeader }", mapOf(), HttpHeaders(headers))
            }
        }
    }

    @Test
    fun queryWithArgument() {
        context.withUserConfiguration(HelloDataFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.executeAndExtractJsonPath<String>("{ hello(name: \"DGS\") }", "data.hello")
            }.isEqualTo("Hello, DGS!")
        }
    }

    @Test
    fun queryWithVariables() {
        context.withUserConfiguration(HelloDataFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.executeAndExtractJsonPath<String>("query(\$name: String) {  hello(name: \$name) }", "data.hello", mapOf(Pair("name", "DGS")))
            }.isEqualTo("Hello, DGS!")
        }
    }

    @Test
    fun queryWithQueryError() {
        context.withUserConfiguration(HelloDataFetcherConfig::class.java).run { ctx ->
            assertThrows<QueryException> {
                ctx.getBean(DgsQueryExecutor::class.java).executeAndExtractJsonPath<String>("{unknown}", "data.unknown")
            }
        }
    }

    @Test
    fun queryWithJsonPathError() {
        context.withUserConfiguration(HelloDataFetcherConfig::class.java).run { ctx ->
            assertThrows<PathNotFoundException> {
                ctx.getBean(DgsQueryExecutor::class.java).executeAndExtractJsonPath<String>("{hello}", "data.unknown")
            }
        }
    }

    @Test
    fun queryDocumentWithArgument() {
        context.withUserConfiguration(HelloDataFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.executeAndGetDocumentContext("{ hello(name: \"DGS\") }").read<String>("data.hello")
            }.isEqualTo("Hello, DGS!")
        }
    }

    @Test
    fun queryDocumentWithVariables() {
        context.withUserConfiguration(HelloDataFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.executeAndGetDocumentContext("query(\$name: String) {  hello(name: \$name) }", mapOf(Pair("name", "DGS"))).read<String>("data.hello")
            }.isEqualTo("Hello, DGS!")
        }
    }

    @Test
    fun queryDocumentWithError() {

        val error: QueryException = assertThrows {
            context.withUserConfiguration(HelloDataFetcherConfig::class.java).run { ctx ->
                assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                    it.executeAndGetDocumentContext("{unknown }")
                }
            }
        }

        assertThat(error.errors.size).isEqualTo(1)
        assertThat(error.errors[0].message).isEqualTo("Validation error of type FieldUndefined: Field 'unknown' in type 'Query' is undefined @ 'unknown'")
    }

    @Test
    fun queryBasicExecute() {
        context.withUserConfiguration(HelloDataFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.execute("{hello}").isDataPresent
            }.isEqualTo(true)
        }
    }

    @Test
    fun queryBasicExecuteWithError() {
        context.withUserConfiguration(HelloDataFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.execute("{unknown}").errors?.size
            }.isEqualTo(1)
        }
    }

    @Test
    fun queryReturnsNullForField() {
        context.withUserConfiguration(HelloDataFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                val execute: ExecutionResult = it.execute("{withNullableNull}")
                tuple(execute.getData<Map<String, String>>()?.get("withNulableNull"), execute.errors?.size)
            }.isEqualTo(tuple(null, 0))
        }
    }

    @Test
    fun queryReturnsErrorForNonNullableField() {
        context.withUserConfiguration(HelloDataFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                val execute = it.execute("{withNonNullableNull}")
                tuple(execute.getData<Map<String, String>>()?.get("withNonNullableNull"), execute.errors?.size)
            }.isEqualTo(tuple(null, 1))
        }
    }
}
