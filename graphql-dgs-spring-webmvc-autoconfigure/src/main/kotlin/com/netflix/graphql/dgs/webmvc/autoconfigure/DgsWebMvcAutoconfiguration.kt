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

package com.netflix.graphql.dgs.webmvc.autoconfigure

import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.mvc.DgsRestController
import com.netflix.graphql.dgs.mvc.DgsRestSchemaJsonController
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnWebApplication
@EnableConfigurationProperties(DgsWebMvcConfigurationProperties::class)
open class DgsWebMvcAutoconfiguration {
    @Bean
    open fun dgsRestController(dgsQueryExecutor: DgsQueryExecutor): DgsRestController {
        return DgsRestController(dgsQueryExecutor)
    }

    @Configuration
    @ConditionalOnProperty(name = ["dgs.graphql.schema-json.enabled"], havingValue = "true", matchIfMissing = true)
    open class DgsWebMvcSchemaJsonConfiguration {
        @Bean
        open fun dgsRestSchemaJsonController(dgsSchemaProvider: DgsSchemaProvider): DgsRestSchemaJsonController {
            return DgsRestSchemaJsonController(dgsSchemaProvider)
        }
    }
}
