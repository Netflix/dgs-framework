/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.graphql.dgs.jackson2

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.graphql.dgs.json.DgsJsonMapper
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean

/**
 * Auto-configuration for DGS Jackson 2 support.
 *
 * When this module is on the classpath, Jackson 2 is used by default.
 * Runs BEFORE DgsSpringGraphQLAutoConfiguration so that the Jackson 2 bean
 * takes precedence over the Jackson 3 default.
 *
 * To override and use Jackson 3 even when this module is present, set:
 *   dgs.graphql.preferred-json-mapper=jackson3
 */
@AutoConfiguration(
    beforeName = ["com.netflix.graphql.dgs.springgraphql.autoconfig.DgsSpringGraphQLAutoConfiguration"],
)
@ConditionalOnClass(ObjectMapper::class)
@ConditionalOnProperty(
    name = ["dgs.graphql.preferred-json-mapper"],
    havingValue = "jackson2",
    matchIfMissing = true,
)
class DgsJackson2AutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(DgsJsonMapper::class)
    fun dgsJsonMapper(): DgsJsonMapper = Jackson2DgsJsonMapper()
}
