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

package com.netflix.graphql.dgs.autoconfig.testcomponents

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactories
import graphql.schema.DataFetcherFactory
import graphql.schema.DataFetchingEnvironment
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

object CustomDataFetcherFactoryFixtures {

    @Configuration
    open class CustomDataFetcherFactoryConfiguration {
        @Bean
        open fun customDataFetcherFactory(): DataFetcherFactory<*> {
            return DataFetcherFactories.useDataFetcher(TestDataFetcher)
        }

        object TestDataFetcher : DataFetcher<Any> {
            override fun get(environment: DataFetchingEnvironment): Any {
                return "not world"
            }
        }
    }

    @DgsComponent
    class ExplicitDataFetcherComponent {
        @DgsData(parentType = "Query", field = "simpleNested")
        fun hello(dfe: DataFetchingEnvironment): SimpleNested {
            return SimpleNested("world")
        }
        data class SimpleNested(val hello: String)
    }
}
