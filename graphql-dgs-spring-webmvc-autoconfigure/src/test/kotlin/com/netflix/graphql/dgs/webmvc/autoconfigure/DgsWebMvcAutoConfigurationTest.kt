/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.graphql.dgs.webmvc.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.mvc.DgsRestController
import com.netflix.graphql.dgs.mvc.DgsRestSchemaJsonController
import graphql.ExecutionResultImpl
import graphql.Scalars
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class DgsWebMvcAutoConfigurationTest {

    private val context = WebApplicationContextRunner().withConfiguration(
        AutoConfigurations.of(
            DgsWebMvcAutoConfiguration::class.java

        )
    )!!.withUserConfiguration(MockUserConfiguration::class.java)

    @Test
    fun objectMapperAvailable() {
        context.run { ctx ->
            Assertions.assertThat(ctx).hasSingleBean(ObjectMapper::class.java)
            Assertions.assertThat(ctx).getBeans(ObjectMapper::class.java).containsKey("dgsObjectMapper")
            val modules = ctx.getBeansOfType(ObjectMapper::class.java)["dgsObjectMapper"]?.registeredModuleIds?.contains("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule")
            Assertions.assertThat(modules).isTrue()
        }
    }

    @Test
    fun graphqlControllerAvailable() {
        context.run { ctx ->
            Assertions.assertThat(ctx).hasSingleBean(DgsRestController::class.java)
        }
    }

    @Test
    fun graphiqlAvailableWhenEnabledPropertyNotSpecified() {
        context.run { ctx ->
            Assertions.assertThat(ctx).hasSingleBean(GraphiQLConfigurer::class.java)
        }
    }

    @Test
    fun graphiqlAvailableWhenEnabledPropertySetToTrue() {
        context.withPropertyValues("dgs.graphql.graphiql.enabled: true").run { ctx ->
            Assertions.assertThat(ctx).hasSingleBean(GraphiQLConfigurer::class.java)
        }
    }

    @Test
    fun graphiqlNotAvailableWhenEnabledPropertySetToFalse() {
        context.withPropertyValues("dgs.graphql.graphiql.enabled: false").run { ctx ->
            Assertions.assertThat(ctx).doesNotHaveBean(GraphiQLConfigurer::class.java)
        }
    }

    @Test
    fun schemaJsonControllerAvailableWhenEnabledPropertyNotSpecified() {
        context.run { ctx ->
            Assertions.assertThat(ctx)
                .hasSingleBean(DgsRestSchemaJsonController::class.java)
        }
    }

    @Test
    fun schemaJsonControllerAvailableWhenEnabledPropertySetToTrue() {
        context.withPropertyValues("dgs.graphql.schema-json.enabled: true").run { ctx ->
            Assertions.assertThat(ctx)
                .hasSingleBean(DgsRestSchemaJsonController::class.java)
        }
    }

    @Test
    fun schemaJsonControllerNotAvailableWhenEnabledPropertySetToFalse() {
        context.withPropertyValues("dgs.graphql.schema-json.enabled: false").run { ctx ->
            Assertions.assertThat(ctx)
                .doesNotHaveBean(DgsRestSchemaJsonController::class.java)
        }
    }

    @Test
    fun graphqlControllerMappedToDefaultPath() {
        context.withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration::class.java,
                WebMvcAutoConfiguration::class.java,
                DispatcherServletAutoConfiguration::class.java
            )
        )
            .run { ctx ->
                val mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build()
                mockMvc.perform(
                    MockMvcRequestBuilders.get("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"{ hello }\"}")
                        .accept(MediaType.APPLICATION_JSON)
                )
                    .andExpect(MockMvcResultMatchers.status().isOk)
                    .andExpect(
                        MockMvcResultMatchers.content()
                            .contentType(MediaType.APPLICATION_JSON)
                    )
            }
    }

    @Test
    fun graphqlControllerMappedToCustomPath() {
        context.withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration::class.java,
                WebMvcAutoConfiguration::class.java,
                DispatcherServletAutoConfiguration::class.java
            )
        )
            .withPropertyValues("dgs.graphql.path: /fooql")
            .run { ctx ->
                val mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build()
                mockMvc.perform(
                    MockMvcRequestBuilders.get("/fooql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"{ hello }\"}")
                        .accept(MediaType.APPLICATION_JSON)
                )
                    .andExpect(MockMvcResultMatchers.status().isOk)
                    .andExpect(
                        MockMvcResultMatchers.content()
                            .contentType(MediaType.APPLICATION_JSON)
                    )
            }
    }

    @Test
    fun schemaJsonControllerMappedToDefaultPath() {
        context.withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration::class.java,
                WebMvcAutoConfiguration::class.java,
                DispatcherServletAutoConfiguration::class.java
            )
        )
            .run { ctx ->
                val mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build()
                mockMvc.perform(
                    MockMvcRequestBuilders.get("/schema.json")
                        .accept(MediaType.APPLICATION_JSON)
                )
                    .andExpect(MockMvcResultMatchers.status().isOk)
                    .andExpect(
                        MockMvcResultMatchers.content()
                            .contentType(MediaType.APPLICATION_JSON)
                    )
            }
    }

    @Test
    fun schemaJsonControllerMappedToCustomPath() {
        context.withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration::class.java,
                WebMvcAutoConfiguration::class.java,
                DispatcherServletAutoConfiguration::class.java
            )
        )
            .withPropertyValues("dgs.graphql.schema-json.path: /foo.json")
            .run { ctx ->
                val mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build()
                mockMvc.perform(
                    MockMvcRequestBuilders.get("/foo.json")
                        .accept(MediaType.APPLICATION_JSON)
                )
                    .andExpect(MockMvcResultMatchers.status().isOk)
                    .andExpect(
                        MockMvcResultMatchers.content()
                            .contentType(MediaType.APPLICATION_JSON)
                    )
            }
    }

    @Configuration
    open class MockUserConfiguration {
        @Bean
        open fun dgsSchemaProvider(): DgsSchemaProvider {
            val objectType: GraphQLObjectType = GraphQLObjectType.newObject()
                .name("helloType")
                .field(
                    GraphQLFieldDefinition.newFieldDefinition()
                        .name("hello")
                        .type(Scalars.GraphQLString)
                )
                .build()
            val schema = GraphQLSchema.newSchema()
                .clearSchemaDirectives()
                .clearAdditionalTypes()
                .clearDirectives()
                .query(objectType).build()
            val mockSchemaProvider = mockk<DgsSchemaProvider>()
            every { mockSchemaProvider.schema() } returns schema
            return mockSchemaProvider
        }

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
            val customMapper = jacksonObjectMapper()
            customMapper.registerModule(JavaTimeModule())
            return customMapper
        }
    }
}
