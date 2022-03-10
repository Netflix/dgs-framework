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

package com.netflix.graphql.dgs.apq.caffeine

import com.netflix.graphql.dgs.Internal
import com.netflix.graphql.dgs.apq.DgsAPQSupportAutoConfiguration
import graphql.execution.preparsed.persisted.PersistedQueryCache
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnMissingBean(DgsCaffeineAPQSupportAutoConfiguration::class)
@AutoConfigureAfter(DgsCaffeineAPQSupportAutoConfiguration::class)
@AutoConfigureBefore(DgsAPQSupportAutoConfiguration::class)
@Internal
open class DgsCaffeineAPQSupportInverseAutoConfiguration {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(DgsCaffeineAPQSupportInverseAutoConfiguration::class.java)
    }

    @Bean
    @ConditionalOnMissingBean(PersistedQueryCache::class)
    open fun onUnusedCaffeineCache(): CommandLineRunner {
        return CommandLineRunner {
            logger.warn(
                "Using DGS Caffeine support for Automatic Persisted Queries (APQ) with " +
                    "`dgs.graphql.apq.enable=false`, " +
                    "consider removing the `graphql-dgs-spring-boot-apq-caffeine` dependency"
            )
        }
    }
}
