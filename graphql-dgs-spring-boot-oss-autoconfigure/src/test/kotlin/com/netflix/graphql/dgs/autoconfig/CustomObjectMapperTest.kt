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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsQueryExecutor
import graphql.schema.DataFetchingEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDateTime

class CustomObjectMapperTest {

    private val context = WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DgsAutoConfiguration::class.java))!!

    @Configuration
    open class CustomObjectMapperConfig {
        @Bean
        @Qualifier("dgsQueryExecutorObjectMapper")
        open fun objectMapper(): ObjectMapper {
            return jacksonObjectMapper()
                .registerModule(JavaTimeModule())
        }

        @Bean
        open fun createDgsComponent(): DateDataFetcher {
            return DateDataFetcher()
        }
    }

    @Test
    fun `When a custom object mapper is registered, it should be used`() {
        context.withUserConfiguration(CustomObjectMapperConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.executeAndExtractJsonPathAsObject(
                    "query { date }",
                    "data.date",
                    LocalDateTime::class.java
                )
            }.isEqualTo(LocalDateTime.of(2022, 6, 15, 14, 30, 0))
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@DgsComponent
class DateDataFetcher {
    @DgsData(parentType = "Query", field = "date")
    fun date(dfe: DataFetchingEnvironment): String {
        return "2022-06-15T14:30:00"
    }
}
