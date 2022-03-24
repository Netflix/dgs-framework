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

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsEntityFetcher
import com.netflix.graphql.dgs.DgsFederationResolver
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.federation.DefaultDgsFederationResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class CustomFederationResolverTest {

    private val context = WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DgsAutoConfiguration::class.java))!!

    @Test
    fun `When a custom federation resolver is registered, it should be used`() {
        context.withUserConfiguration(MyFederationConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                val representation = mapOf(Pair("__typename", "MyMovie"), Pair("id", "1"))
                val variables = mapOf(Pair("representations", listOf(representation)))

                val title = it.executeAndExtractJsonPathAsObject(
                    """query (${'$'}representations:[_Any!]!) { 
                                _entities(representations:${'$'}representations) {
                                    ... on MyMovie {   
                                        title 
                                    } 
                                }
                            }""",
                    "data['_entities'][0].title", variables, String::class.java
                )

                assertThat(title).isEqualTo("some title")
            }
        }
    }

    @Configuration
    open class MyFederationConfig {
        @Bean
        open fun federationResolver(): DgsFederationResolver {
            return MyFederationResolver()
        }

        @Bean
        open fun movieFetcher(): MovieDataFetcher {
            return MovieDataFetcher()
        }
    }

    @DgsComponent
    class MovieDataFetcher {
        @DgsEntityFetcher(name = "MyMovie")
        fun movieEntityFetcher(arguments: Map<String, Any>): Movie {
            return Movie()
        }
    }

    class MyFederationResolver : DefaultDgsFederationResolver() {
        override fun typeMapping(): Map<Class<*>, String> {
            return mapOf(Pair(Movie::class.java, "MyMovie"))
        }
    }

    class Movie {
        val title: String = "some title"
    }
}
