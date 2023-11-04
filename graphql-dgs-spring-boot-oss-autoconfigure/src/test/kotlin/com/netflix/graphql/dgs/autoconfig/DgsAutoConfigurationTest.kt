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

package com.netflix.graphql.dgs.autoconfig

import com.netflix.graphql.dgs.DgsDataLoaderOptionsProvider
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.autoconfig.testcomponents.CustomContextBuilderConfig
import com.netflix.graphql.dgs.autoconfig.testcomponents.CustomDataFetcherFactoryFixtures
import com.netflix.graphql.dgs.autoconfig.testcomponents.CustomInputObjectMapperConfig
import com.netflix.graphql.dgs.autoconfig.testcomponents.DataFetcherWithInputObject
import com.netflix.graphql.dgs.autoconfig.testcomponents.DataLoaderConfig
import com.netflix.graphql.dgs.autoconfig.testcomponents.HelloDataFetcherConfig
import com.netflix.graphql.dgs.exceptions.NoSchemaFoundException
import com.netflix.graphql.dgs.internal.InputObjectMapper
import com.netflix.graphql.dgs.internal.method.ContinuationArgumentResolver
import com.netflix.graphql.dgs.internal.method.DataFetchingEnvironmentArgumentResolver
import com.netflix.graphql.dgs.internal.method.FallbackEnvironmentArgumentResolver
import com.netflix.graphql.dgs.internal.method.InputArgumentResolver
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.core.io.ClassPathResource

class DgsAutoConfigurationTest {
    private val context =
        WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(DgsAutoConfiguration::class.java))!!

    @Test
    fun noSchemaException() {
        context.withClassLoader(FilteredClassLoader(ClassPathResource("schema/"))).run { ctx ->
            assertThat(ctx).failure.hasRootCauseInstanceOf(NoSchemaFoundException::class.java)
        }
    }

    @Test
    fun setUpCustomDataFetcherFactory() {
        context.withUserConfiguration(
            CustomDataFetcherFactoryFixtures.CustomDataFetcherFactoryConfiguration::class.java,
            CustomDataFetcherFactoryFixtures.ExplicitDataFetcherComponent::class.java
        )
            .run { ctx ->
                assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                    val executeQuery =
                        it.executeAndExtractJsonPath<String>(
                            "query {simpleNested{hello}}",
                            "data.simpleNested.hello"
                        )
                    assertThat(executeQuery).isEqualTo("not world")
                }
            }
    }

    @Test
    fun setsUpQueryExecutorWithDataFetcher() {
        context.withUserConfiguration(HelloDataFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                val executeQuery = it.executeAndExtractJsonPath<String>("query {hello}", "data.hello")
                assertThat(executeQuery).isEqualTo("Hello!")
            }
        }
    }

    @Test
    fun dataLoaderGetsRegistered() {
        context.withUserConfiguration(DataLoaderConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                val json = it.executeAndExtractJsonPath<List<String>>("{names}", "data.names")
                assertThat(json).isEqualTo(listOf("A", "B", "C"))
            }
        }
    }

    @Test
    fun mappedDataLoaderGetsRegistered() {
        context.withUserConfiguration(DataLoaderConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                val json = it.executeAndExtractJsonPath<List<String>>(
                    "{namesFromMapped}",
                    "data.namesFromMapped"
                )
                assertThat(json).isEqualTo(listOf("A", "B", "C"))
            }
        }
    }

    @Test
    fun customContext() {
        context.withUserConfiguration(CustomContextBuilderConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                val json = it.executeAndExtractJsonPath<Any>("{hello}", "data.hello")
                assertThat(json).isEqualTo("Hello custom context")
            }
        }
    }

    @Test
    fun enabledIntrospectionTest() {
        context.withUserConfiguration(CustomContextBuilderConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                @Language("graphql")
                val query = """
                     query availableQueries {
                          __schema {
                            queryType {
                              fields {
                                name
                                description
                              }
                            }
                          }
                        }
                """.trimIndent()

                val json = it.executeAndExtractJsonPath<Any>(
                    query,
                    "data.__schema.queryType.fields[0].name"
                )
                assertThat(json).isEqualTo("hello")
            }
        }
    }

    @Test
    fun `DGS Input Argument beans are available`() {
        context.run { ctx ->
            assertThat(ctx).getBean("defaultInputObjectMapper", InputObjectMapper::class.java).isNotNull
            assertThat(ctx).getBean("inputArgumentResolver", InputArgumentResolver::class.java).isNotNull
            assertThat(ctx).getBean("dataFetchingEnvironmentArgumentResolver", DataFetchingEnvironmentArgumentResolver::class.java).isNotNull
            assertThat(ctx).getBean("coroutineArgumentResolver", ContinuationArgumentResolver::class.java).isNotNull
            assertThat(ctx).getBean("fallbackEnvironmentArgumentResolver", FallbackEnvironmentArgumentResolver::class.java).isNotNull
        }
    }

    @Test
    fun `DGS custom data loader options beans is available and used`() {
        context.withUserConfiguration(DataLoaderConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsDataLoaderOptionsProvider::class.java).isNotNull()
            assertThat(ctx).getBean(DgsDataLoaderOptionsProvider::class.java).javaClass.simpleName.equals("CustomDataLoaderOptionsProvider")
        }
    }

    @Test
    fun `DGS default data loader options bean is available`() {
        context.run { ctx ->
            assertThat(ctx).getBean(DgsDataLoaderOptionsProvider::class.java).isNotNull()
            assertThat(ctx).getBean(DgsDataLoaderOptionsProvider::class.java).javaClass.simpleName.equals("DefaultDataLoaderOptionsProvider")
        }
    }

    @Test
    fun `It should be possible to override default input object mapper`() {
        context.withUserConfiguration(CustomInputObjectMapperConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                @Language("graphql")
                val query = """
                    query withIgnoredFields {
                        withIgnoredField(
                            input: { ignoredField: "this should be ignored", name: "this should be included" }
                        ) {
                            ignoredField
                            name                                             
                        }
                    }
                """.trimIndent()

                it.executeAndExtractJsonPathAsObject(
                    query,
                    "data.withIgnoredField",
                    DataFetcherWithInputObject.Input::class.java
                )
            }.isEqualTo(DataFetcherWithInputObject.Input(null, "this should be included"))
        }
    }

    @Test
    fun `Nested input objects should use overridden input object mapper`() {
        context.withUserConfiguration(CustomInputObjectMapperConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                @Language("graphql")
                val query: String = """
                    query withIgnoredFields {
                        withIgnoredFieldNested(
                            nestedInput: {
                                input: { ignoredField: "this should be ignored", name: "this should be included" }
                            }
                        ) {
                            ignoredField
                            name                                             
                        }
                    } 
                """.trimIndent()

                it.executeAndExtractJsonPathAsObject(
                    query,
                    "data.withIgnoredFieldNested",
                    DataFetcherWithInputObject.Input::class.java
                )
            }.isEqualTo(DataFetcherWithInputObject.Input(null, "this should be included"))
        }
    }
}
