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
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.internal.CookieValueResolver
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.mvc.DgsRestController
import com.netflix.graphql.dgs.mvc.DgsRestSchemaJsonController
import com.netflix.graphql.dgs.mvc.ServletCookieValueResolver
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.servlet.DispatcherServlet

@Configuration
@ConditionalOnWebApplication
@EnableConfigurationProperties(DgsWebMvcConfigurationProperties::class)
open class DgsWebMvcAutoConfiguration {
    @Bean
    @Qualifier("dgsObjectMapper")
    @ConditionalOnMissingBean(name = ["dgsObjectMapper"])
    open fun dgsObjectMapper(): ObjectMapper {
        return jacksonObjectMapper()
    }

    @Bean
    open fun dgsRestController(dgsQueryExecutor: DgsQueryExecutor, @Qualifier("dgsObjectMapper") objectMapper: ObjectMapper): DgsRestController {
        return DgsRestController(dgsQueryExecutor, objectMapper)
    }

    @Bean
    open fun servletCookieValueResolver(): CookieValueResolver {
        return ServletCookieValueResolver()
    }

    @Configuration
    @ConditionalOnClass(DispatcherServlet::class)
    @ConditionalOnProperty(name = ["dgs.graphql.graphiql.enabled"], havingValue = "true", matchIfMissing = true)
    @Import(GraphiQLConfigurer::class)
    open class DgsGraphiQLConfiguration

    @Configuration
    @ConditionalOnProperty(name = ["dgs.graphql.schema-json.enabled"], havingValue = "true", matchIfMissing = true)
    open class DgsWebMvcSchemaJsonConfiguration {
        @Bean
        open fun dgsRestSchemaJsonController(dgsSchemaProvider: DgsSchemaProvider): DgsRestSchemaJsonController {
            return DgsRestSchemaJsonController(dgsSchemaProvider)
        }
    }
}
