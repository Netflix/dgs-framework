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

package com.netflix.graphql.dgs.autoconfigure.testcomponents

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.context.DgsCustomContextBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class CustomContextBuilderConfig {
    @Bean
    open fun createCustomContextBuilder(): DgsCustomContextBuilder<*> {
        return MyCustomDgsContextBuilder()
    }

    @Bean
    open fun createDataFetcher(): CustomContextDataFetcher {
        return CustomContextDataFetcher()
    }
}

class MyCustomDgsContextBuilder : DgsCustomContextBuilder<MyCustomContext> {
    override fun build(): MyCustomContext {
        return MyCustomContext("Hello custom context")
    }
}

class MyCustomContext(val message: String)

@DgsComponent
class CustomContextDataFetcher {
    @DgsData(parentType = "Query", field = "hello")
    fun hello(dfe: DataFetchingEnvironment): String {
        val customContext = DgsContext.getCustomContext<MyCustomContext>(dfe)
        return customContext.message
    }
}
