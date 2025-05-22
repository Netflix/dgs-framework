/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.graphql.dgs.springgraphql.autoconfig

import org.springframework.boot.SpringApplication
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import java.util.Collections
import java.util.stream.Collectors

/**
 * Globally disable AutoConfig's which cause problems in the Netflix environment
 */
@Order(Ordered.LOWEST_PRECEDENCE - 1)
class ExcludeAutoConfigurationsEnvironmentPostProcessor : EnvironmentPostProcessor {
    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        val existingExcludes = extractAllExcludes(environment.propertySources)
        val disabled =
            DISABLE_AUTOCONFIG_PROPERTIES
                .asSequence()
                .filter { !environment.getProperty(it.key, Boolean::class.java, false) }
                .map { it.value }
                .plus(existingExcludes)
                .filter { it.isNotEmpty() }
                .joinToString(",")

        environment.propertySources
            .addFirst(
                MapPropertySource(
                    "disableRefreshScope",
                    Collections.singletonMap<String, Any>(
                        "spring.autoconfigure.exclude",
                        disabled,
                    ),
                ),
            )
    }

    private fun extractAllExcludes(propertySources: MutablePropertySources): String {
        val testExclude = propertySources.find { it.name == INLINED_TEST_PROPERTIES }?.getProperty(EXCLUDE)
        if (testExclude != null && testExclude is String && testExclude.isNotBlank()) {
            return testExclude
        }

        return propertySources
            .stream()
            .filter { src -> !ConfigurationPropertySources.isAttachedConfigurationPropertySource(src) }
            .map { src ->
                Binder(ConfigurationPropertySources.from(src))
                    .bind(EXCLUDE, Array<String>::class.java)
                    .map {
                        it.toList()
                    }.orElse(emptyList())
            }.flatMap { it.stream() }
            .collect(Collectors.joining(","))
    }

    companion object {
        private val DISABLE_AUTOCONFIG_PROPERTIES =
            mapOf(
                Pair(
                    "dgs.springgraphql.autoconfiguration.graphqlobservation.enabled",
                    "org.springframework.boot.actuate.autoconfigure.observation.graphql.GraphQlObservationAutoConfiguration",
                ),
                Pair(
                    "dgs.springgraphql.autoconfiguration.graphqlwebmvcsecurity.enabled",
                    "org.springframework.boot.autoconfigure.graphql.security.GraphQlWebMvcSecurityAutoConfiguration",
                ),
            )

        private const val EXCLUDE = "spring.autoconfigure.exclude"
        private const val INLINED_TEST_PROPERTIES = "Inlined Test Properties"
    }
}
