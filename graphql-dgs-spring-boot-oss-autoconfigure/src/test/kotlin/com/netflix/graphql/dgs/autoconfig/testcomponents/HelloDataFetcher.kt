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

package com.netflix.graphql.dgs.autoconfig.testcomponents

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import graphql.schema.DataFetchingEnvironment
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/*
    Config class for the Hello data fetcher test
     */
@Configuration
open class HelloDatFetcherConfig {
    @Bean
    open fun createDgsComponent(): HelloDataFetcher {
        return HelloDataFetcher()
    }
}

@Suppress("UNUSED_PARAMETER")
@DgsComponent
class HelloDataFetcher {
    @DgsData(parentType = "Query", field = "hello")
    fun hello(dfe: DataFetchingEnvironment): String {
        if (dfe.arguments["name"] != null) {
            return "Hello, ${dfe.arguments["name"]}!"
        }

        return "Hello!"
    }

    @DgsData(parentType = "Query", field = "withNullableNull")
    fun withNullableNull(): String? {
        return null
    }

    @DgsData(parentType = "Query", field = "withNonNullableNull")
    fun withNonNullableNull(): String? {
        return null
    }
}
