/*
 * Copyright 2023 Netflix, Inc.
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

package com.netflix.graphql.dgs.subscriptions.websockets.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.subscriptions.websockets.DgsWebSocketAutoConfig
import graphql.ExecutionResultImpl
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class DgsWebSocketAutoConfigTest {
    private val context =
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DgsWebSocketAutoConfig::class.java))
            .withUserConfiguration(MockUserConfiguration::class.java)

    @Test
    fun objectMapperAvailable() {
        context.run { ctx ->
            Assertions.assertThat(ctx).hasSingleBean(ObjectMapper::class.java)
            Assertions.assertThat(ctx).getBeans(ObjectMapper::class.java).containsKey("dgsObjectMapper")
            // Expecting the JavaTimeModule from the dgsObjectMapper provided via MockUserConfiguration
            val modules = ctx.getBeansOfType(ObjectMapper::class.java)["dgsObjectMapper"]
                ?.registeredModuleIds?.contains(MockUserConfiguration.jacksonJavaTimeModule.moduleName)
            Assertions.assertThat(modules).isTrue()
        }
    }

    @Configuration
    open class MockUserConfiguration {

        @Bean
        open fun dgsQueryExecutor(): DgsQueryExecutor {
            val mockExecutor = mockk<DgsQueryExecutor>()
            every {
                mockExecutor.execute(
                    "{ hello }",
                    any(),
                    any(),
                    any(),
                    null,
                    any()
                )
            } returns ExecutionResultImpl.newExecutionResult()
                .data(mapOf(Pair("hi", "there"))).build()
            return mockExecutor
        }

        @Bean
        @Qualifier("dgsObjectMapper")
        open fun dgsObjectMapper(): ObjectMapper {
            return jacksonObjectMapper().registerModule(jacksonJavaTimeModule)
        }

        companion object {
            val jacksonJavaTimeModule = JavaTimeModule()
        }
    }
}
