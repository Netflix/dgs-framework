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

package com.netflix.graphql.dgs.apq

import com.netflix.graphql.dgs.Internal
import com.netflix.graphql.dgs.internal.QueryValueCustomizer
import graphql.execution.preparsed.persisted.ApolloPersistedQuerySupport
import graphql.execution.preparsed.persisted.PersistedQueryCache
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(
    prefix = DgsAPQSupportProperties.PREFIX,
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = DgsAPQSupportProperties.DEFAULT_ENABLE_APQ
)
@EnableConfigurationProperties(DgsAPQSupportProperties::class)
@Internal
open class DgsAPQSupportAutoConfiguration {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(DgsAPQSupportAutoConfiguration::class.java)
    }

    @Bean
    @ConditionalOnBean(PersistedQueryCache::class)
    open fun apolloPersistedQuerySupport(persistedQueryCache: PersistedQueryCache): ApolloPersistedQuerySupport {
        if (logger.isInfoEnabled) {
            logger.info("Using Automatic Persisted Queries (APQ) support by ${persistedQueryCache.javaClass.simpleName}")
        }
        return ApolloPersistedQuerySupport(persistedQueryCache)
    }

    @Bean
    @ConditionalOnMissingBean(PersistedQueryCache::class)
    open fun missingPersistedQuerySupport(ctx: ApplicationContext): CommandLineRunner {
        if (logger.isErrorEnabled) {
            logger.error(
                "Configuration property `dgs.graphql.apq.enabled` was `true`, but no PersistedQueryCache bean was defined",
                DgsNoPersistedQueryCacheBeanDefinedException()
            )
        }
        SpringApplication.exit(ctx, { 1 })
        return CommandLineRunner { }
    }

    @Bean
    @ConditionalOnBean(ApolloPersistedQuerySupport::class)
    open fun apolloAPQQueryValueCustomizer(): QueryValueCustomizer {
        return QueryValueCustomizer { query ->
            if (query.isNullOrBlank()) {
                ApolloPersistedQuerySupport.PERSISTED_QUERY_MARKER
            } else {
                query
            }
        }
    }
}
