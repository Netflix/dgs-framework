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
import com.netflix.graphql.dgs.apq.DgsAPQSupportProperties
import graphql.execution.preparsed.persisted.PersistedQueryCache
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.ClassUtils

@Configuration
@AutoConfigureAfter(DgsCaffeineAPQSupportAutoConfiguration::class)
@AutoConfigureBefore(DgsAPQSupportAutoConfiguration::class)
@EnableConfigurationProperties(DgsCaffeineAPQSupportProperties::class)
@Internal
open class DgsCaffeineAPQSupportInverseAutoConfiguration(
    private val caffeineProperties: DgsCaffeineAPQSupportProperties,
    private val apqProperties: DgsAPQSupportProperties,
    private val applicationContext: ApplicationContext
) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(DgsCaffeineAPQSupportInverseAutoConfiguration::class.java)
    }

    @Bean
    open fun testDgsCaffeineSupport(): CommandLineRunner {
        runTests() // Run on bean init
        return CommandLineRunner { /* Do nothing */ }
    }

    private fun runTests() {
        logger.info("TEST123")
        if (!ClassUtils.isPresent("com.github.benmanes.caffeine.cache.Cache", javaClass.classLoader)) {
            logger.error(
                "Using DGS Caffeine support for Automatic Persisted Queries (APQ), but Caffeine was not present at runtime"
            )
        } else if (!apqProperties.enabled) {
            logger.warn(
                "Using DGS Caffeine support for Automatic Persisted Queries (APQ) with " +
                        "`dgs.graphql.apq.enable=false`, " +
                        "consider removing the `graphql-dgs-spring-boot-apq-caffeine` dependency"
            )
        } else if (caffeineProperties.enabled) {
            if (!applicationContext.containsBean(DgsCaffeineApqCache.CAFFEINE_APQ_CACHE_BEAN_NAME)) {
                logger.warn(
                    "Using DGS Caffeine support for Automatic Persisted Queries (APQ) with " +
                            "`dgs.graphql.apq.default-cache.enabled=true`, but the application was configured incorrectly"
                )
            } else {
                val theBean: PersistedQueryCache? = try {
                    applicationContext.getBean(PersistedQueryCache::class.java)
                } catch (e: NoSuchBeanDefinitionException) {
                    null
                }

                val theCorrectBean = applicationContext.containsBean(
                    DgsCaffeineAPQSupportAutoConfiguration.APQBasicCaffeineCacheConfiguration.BEAN_NAME
                )

                if (theCorrectBean) {
                    logger.info("The DGS support for Automatic Persisted Queries (APQ) is using built-in Caffeine support")
                } else if (theBean == null) {
                    logger.warn(
                        "Using DGS Caffeine support for Automatic Persisted Queries (APQ) with " +
                                "`dgs.graphql.apq.default-cache.enabled=true`, but no PersistedQueryCache bean was found," +
                                " this should never happen"
                    )
                } else {
                    logger.warn(
                        "Using DGS Caffeine support for Automatic Persisted Queries (APQ) with " +
                                "`dgs.graphql.apq.default-cache.enabled=true`, but another PersistedQueryCache was present, " +
                                "making the `graphql-dgs-spring-boot-apq-caffeine` dependency redundant, consider removing it"
                    )
                }
            }
        } else {
            try {
                applicationContext.getBean(PersistedQueryCache::class.java)
                // else -> catch
                logger.warn(
                    "Using DGS Caffeine support for Automatic Persisted Queries (APQ) with " +
                            "`dgs.graphql.apq.enable=true`, and another PersistedQueryCache was present, " +
                            "making the `graphql-dgs-spring-boot-apq-caffeine` dependency redundant, consider removing it"
                )
            } catch (e: NoSuchBeanDefinitionException) {
                logger.warn(
                    "Using DGS Caffeine support for Automatic Persisted Queries (APQ) with " +
                            "`dgs.graphql.apq.default-cache.enabled=false`, but no other PersistedQueryCache bean was found, " +
                            "consider removing the `graphql-dgs-spring-boot-apq-caffeine` dependency, or enabling " +
                            "the `dgs.graphql.apq.default-cache.enabled` property"
                )
            }
        }
    }
}
