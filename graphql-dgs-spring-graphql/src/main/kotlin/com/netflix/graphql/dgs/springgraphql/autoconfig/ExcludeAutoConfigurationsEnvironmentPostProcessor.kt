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

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySource
import org.springframework.core.env.getProperty

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
                .filter { !environment.getProperty<Boolean>(it.key, false) }
                .map { it.value }
                .plus(existingExcludes)
                .filter { it.isNotEmpty() }
                .joinToString(",")

        environment.propertySources
            .addFirst(
                MapPropertySource(
                    "disableRefreshScope",
                    mapOf(
                        "spring.autoconfigure.exclude" to
                            disabled,
                    ),
                ),
            )
    }

    private fun extractAllExcludes(propertySources: MutablePropertySources): String {
        val testExclude = propertySources.find { it.name == INLINED_TEST_PROPERTIES }?.let { excludesFrom(it) }
        if (!testExclude.isNullOrEmpty()) {
            return testExclude.joinToString(",")
        }

        return propertySources
            .asSequence()
            .filter { src -> !ConfigurationPropertySources.isAttachedConfigurationPropertySource(src) }
            .flatMap { src -> excludesFrom(src).asSequence() }
            .joinToString(",")
    }

    /**
     * Collects exclude class names from a property source.
     *
     * Spring Boot accepts several representations of `spring.autoconfigure.exclude`:
     * a comma-separated string, a YAML list (stored as a [Collection] or array),
     * and index-based keys such as `spring.autoconfigure.exclude[0]`.
     * Only the string/array forms were previously merged, so user YAML lists and
     * indexed properties were overwritten by this post-processor.
     */
    private fun excludesFrom(src: PropertySource<*>): List<String> {
        val fromDirect =
            when (val property = src.getProperty(EXCLUDE)) {
                is String -> property.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                is Array<*> -> property.filterIsInstance<String>().map { it.trim() }.filter { it.isNotEmpty() }
                is Collection<*> -> property.filterIsInstance<String>().map { it.trim() }.filter { it.isNotEmpty() }
                else -> emptyList()
            }
        if (fromDirect.isNotEmpty()) {
            return fromDirect
        }

        val indexed = mutableListOf<String>()
        var i = 0
        while (true) {
            val value = src.getProperty("$EXCLUDE[$i]") ?: break
            val str = value.toString().trim()
            if (str.isNotEmpty()) {
                indexed.add(str)
            }
            i++
        }
        return indexed
    }

    companion object {
        private val DISABLE_AUTOCONFIG_PROPERTIES =
            mapOf(
                "dgs.springgraphql.autoconfiguration.graphqlobservation.enabled" to
                    "org.springframework.boot.graphql.autoconfigure.observation.GraphQlObservationAutoConfiguration",
                "dgs.springgraphql.autoconfiguration.graphqlwebmvcsecurity.enabled" to
                    "org.springframework.boot.graphql.autoconfigure.security.GraphQlWebMvcSecurityAutoConfiguration",
            )

        private const val EXCLUDE = "spring.autoconfigure.exclude"
        private const val INLINED_TEST_PROPERTIES = "Inlined Test Properties"
    }
}
