/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.graphql.dgs.springgraphql.autoconfig

import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.mvc.internal.method.HandlerMethodArgumentResolverAdapter
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import com.netflix.graphql.dgs.reactive.internal.DefaultDgsReactiveGraphQLContextBuilder
import com.netflix.graphql.dgs.reactive.internal.method.SyncHandlerMethodArgumentResolverAdapter
import com.netflix.graphql.dgs.springgraphql.webflux.DgsWebFluxGraphQLInterceptor
import com.netflix.graphql.dgs.springgraphql.webmvc.DgsWebMvcGraphQLInterceptor
import graphql.ErrorClassification
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.graphql.execution.GraphQlSource
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter
import org.springframework.web.reactive.BindingContext

class DgsSpringGraphQlAutoConfigurationTest {
    private val autoConfigurations =
        AutoConfigurations.of(
            DgsSpringGraphQLAutoConfiguration::class.java,
            GraphQlAutoConfiguration::class.java,
        )

    @Test
    fun shouldContributeBeans() {
        val contextRunner =
            ApplicationContextRunner()
                .withConfiguration(autoConfigurations)

        contextRunner.run { context ->
            assertThat(context)
                .hasSingleBean(DgsQueryExecutor::class.java)
                .hasSingleBean(DgsSpringGraphQLAutoConfiguration.DgsRuntimeWiringConfigurerBridge::class.java)
                .hasSingleBean(DgsSpringGraphQLAutoConfiguration.DgsTypeDefinitionConfigurerBridge::class.java)
                .hasSingleBean(GraphQlSourceBuilderCustomizer::class.java)
                .hasSingleBean(DgsSchemaProvider::class.java)
                .hasSingleBean(GraphQlSource::class.java)
        }
    }

    @Test
    fun shouldContributeWebMvcBeans() {
        val webContextRunner =
            WebApplicationContextRunner()
                .withConfiguration(autoConfigurations)

        webContextRunner.run { context ->
            assertThat(context)
                .hasSingleBean(DgsWebMvcGraphQLInterceptor::class.java)
                .hasSingleBean(WebDataBinderFactory::class.java)

            assertThat(context).getBean("requestHeaderMapResolver").isExactlyInstanceOf(
                HandlerMethodArgumentResolverAdapter::class.java,
            )

            assertThat(context).getBean("requestHeaderResolver").isExactlyInstanceOf(
                HandlerMethodArgumentResolverAdapter::class.java,
            )

            assertThat(context).getBean("requestParamResolver").isExactlyInstanceOf(
                HandlerMethodArgumentResolverAdapter::class.java,
            )

            assertThat(context).getBean("requestParamMapResolver").isExactlyInstanceOf(
                HandlerMethodArgumentResolverAdapter::class.java,
            )

            assertThat(context).getBean("cookieValueResolver").isExactlyInstanceOf(
                HandlerMethodArgumentResolverAdapter::class.java,
            )
        }
    }

    @Test
    fun shouldContributeWebfluxBeans() {
        val reactiveContextRunner =
            ReactiveWebApplicationContextRunner()
                .withConfiguration(autoConfigurations)
                .withBean(ReactiveAdapterRegistry::class.java)

        reactiveContextRunner.run { context ->
            assertThat(context)
                .hasSingleBean(DgsReactiveQueryExecutor::class.java)
                .hasSingleBean(DefaultDgsReactiveGraphQLContextBuilder::class.java)
                .hasSingleBean(ServerWebExchangeContextFilter::class.java)
                .hasSingleBean(DgsWebFluxGraphQLInterceptor::class.java)

            assertThat(context).getBean("dgsBindingContext").isExactlyInstanceOf(
                BindingContext::class.java,
            )

            assertThat(context).getBean("cookieValueArgumentResolver").isExactlyInstanceOf(
                SyncHandlerMethodArgumentResolverAdapter::class.java,
            )

            assertThat(context).getBean("requestHeaderMapArgumentResolver").isExactlyInstanceOf(
                SyncHandlerMethodArgumentResolverAdapter::class.java,
            )

            assertThat(context).getBean("requestHeaderArgumentResolver").isExactlyInstanceOf(
                SyncHandlerMethodArgumentResolverAdapter::class.java,
            )

            assertThat(context).getBean("requestParamArgumentResolver").isExactlyInstanceOf(
                SyncHandlerMethodArgumentResolverAdapter::class.java,
            )

            assertThat(context).getBean("requestParamMapArgumentResolver").isExactlyInstanceOf(
                SyncHandlerMethodArgumentResolverAdapter::class.java,
            )
        }
    }

    @Test
    fun loadsDefaultWebMvcConfiguration() {
        ApplicationContextRunner()
            .withConfiguration(autoConfigurations)
            .run { context ->
                assertThat(
                    context
                        .getBean(DgsSpringGraphQLConfigurationProperties::class.java)
                        .webmvc.asyncdispatch.enabled,
                ).isFalse()
            }
    }

    @Test
    fun loadsWebMvcConfiguration() {
        ApplicationContextRunner()
            .withConfiguration(autoConfigurations)
            .withPropertyValues("dgs.graphql.spring.webmvc.asyncdispatch.enabled=true")
            .run { context ->
                assertThat(
                    context
                        .getBean(DgsSpringGraphQLConfigurationProperties::class.java)
                        .webmvc.asyncdispatch.enabled,
                ).isTrue()
            }
    }

    @Test
    fun loadsWebMvcConfigurationDisabled() {
        ApplicationContextRunner()
            .withConfiguration(autoConfigurations)
            .withPropertyValues("dgs.graphql.spring.webmvc.asyncdispatch.enabled=false")
            .run { context ->
                assertThat(
                    context
                        .getBean(DgsSpringGraphQLConfigurationProperties::class.java)
                        .webmvc.asyncdispatch.enabled,
                ).isFalse()
            }
    }

    @Test
    fun introspectionDefaultPropertyTest() {
        ApplicationContextRunner()
            .withConfiguration(autoConfigurations)
            .run { context ->
                // Introspection enabled have default config values in a Spring EnvironmentPostProcessor.
                DgsSpringGraphQLEnvironmentPostProcessor().postProcessEnvironment(
                    context.environment,
                    SpringApplication(context.sourceApplicationContext),
                )

                // Check expected config values.
                assertThat(context.environment.getProperty("spring.graphql.schema.introspection.enabled")).isEqualTo("true")
                assertThat(context.environment.getProperty("dgs.graphql.introspection.enabled")).isEqualTo(null)

                // Check expected results.
                assertThat(context).getBean(DgsQueryExecutor::class.java).extracting {
                    val response =
                        it.execute(
                            " query availableQueries {\n" +
                                "  __schema {\n" +
                                "    queryType {\n" +
                                "      fields {\n" +
                                "        name\n" +
                                "        description\n" +
                                "      }\n" +
                                "    }\n" +
                                "  }\n" +
                                "}",
                        )
                    assertThat(response.errors.size).isEqualTo(0)
                    assertThat(response.isDataPresent).isTrue()
                }
            }
    }

    @Test
    fun introspectionDisabledWithSpringPropertyTest() {
        ApplicationContextRunner()
            .withConfiguration(autoConfigurations)
            .withPropertyValues("spring.graphql.schema.introspection.enabled=false")
            .run { context ->
                // Introspection enabled have default config values in a Spring EnvironmentPostProcessor.
                DgsSpringGraphQLEnvironmentPostProcessor().postProcessEnvironment(
                    context.environment,
                    SpringApplication(context.sourceApplicationContext),
                )

                // Check expected config values.
                assertThat(context.environment.getProperty("spring.graphql.schema.introspection.enabled")).isEqualTo("false")
                assertThat(context.environment.getProperty("dgs.graphql.introspection.enabled")).isEqualTo(null)

                // Check expected results.
                assertThat(context).getBean(DgsQueryExecutor::class.java).extracting {
                    val response =
                        it.execute(
                            " query availableQueries {\n" +
                                "  __schema {\n" +
                                "    queryType {\n" +
                                "      fields {\n" +
                                "        name\n" +
                                "        description\n" +
                                "      }\n" +
                                "    }\n" +
                                "  }\n" +
                                "}",
                        )
                    assertThat(response.errors.size).isEqualTo(1)
                    assertThat(response.isDataPresent).isFalse()
                    assertThat(
                        response.errors
                            .first()
                            .errorType
                            .toString(),
                    ).isEqualTo(ErrorClassification.errorClassification("IntrospectionDisabled").toString())
                }
            }
    }

    @Test
    fun introspectionEnabledWithSpringPropertyTest() {
        ApplicationContextRunner()
            .withConfiguration(autoConfigurations)
            .withPropertyValues("spring.graphql.schema.introspection.enabled=true")
            .run { context ->
                // Check expected config values.
                assertThat(context.environment.getProperty("spring.graphql.schema.introspection.enabled")).isEqualTo("true")
                assertThat(context.environment.getProperty("dgs.graphql.introspection.enabled")).isEqualTo(null)

                // Check expected results.
                assertThat(context).getBean(DgsQueryExecutor::class.java).extracting {
                    val response =
                        it.execute(
                            " query availableQueries {\n" +
                                "  __schema {\n" +
                                "    queryType {\n" +
                                "      fields {\n" +
                                "        name\n" +
                                "        description\n" +
                                "      }\n" +
                                "    }\n" +
                                "  }\n" +
                                "}",
                        )
                    assertThat(response.errors.size).isEqualTo(0)
                    assertThat(response.isDataPresent).isTrue()
                }
            }
    }

    @Test
    fun introspectionEnabledWithDgsPropertyTest() {
        ApplicationContextRunner()
            .withConfiguration(autoConfigurations)
            .withPropertyValues("dgs.graphql.introspection.enabled=true")
            .run { context ->
                // Introspection enabled have default config values in a Spring EnvironmentPostProcessor.
                DgsSpringGraphQLEnvironmentPostProcessor().postProcessEnvironment(
                    context.environment,
                    SpringApplication(context.sourceApplicationContext),
                )

                // Check expected config values.
                assertThat(context.environment.getProperty("spring.graphql.schema.introspection.enabled")).isEqualTo("true")
                assertThat(context.environment.getProperty("dgs.graphql.introspection.enabled")).isEqualTo("true")

                // Check expected results.
                assertThat(context).getBean(DgsQueryExecutor::class.java).extracting {
                    val response =
                        it.execute(
                            " query availableQueries {\n" +
                                "  __schema {\n" +
                                "    queryType {\n" +
                                "      fields {\n" +
                                "        name\n" +
                                "        description\n" +
                                "      }\n" +
                                "    }\n" +
                                "  }\n" +
                                "}",
                        )
                    assertThat(response.errors.size).isEqualTo(0)
                    assertThat(response.isDataPresent).isTrue()
                }
            }
    }
}
