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
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import java.util.Collections

/**
 * Globally disable AutoConfig's which cause problems in the Netflix environment
 */
@Order(Ordered.LOWEST_PRECEDENCE - 1)
class ExcludeAutoConfigurationsEnvironmentPostProcessor : EnvironmentPostProcessor {
    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        val existingExcludes = extractAllExcludes(environment)
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

    private fun extractAllExcludes(environment: ConfigurableEnvironment): String {
        return Binder.get(environment)
            .bind(EXCLUDE, Array<String>::class.java)
            .orElse(emptyArray<String>())
            ?.filter { it.isNotBlank() }
            ?.joinToString(",") ?: ""
    }

    companion object {
        private val DISABLE_AUTOCONFIG_PROPERTIES =
            mapOf(
                Pair(
                    "dgs.springgraphql.autoconfiguration.graphqlobservation.enabled",
                    "org.springframework.boot.graphql.autoconfigure.observation.GraphQlObservationAutoConfiguration",
                ),
                Pair(
                    "dgs.springgraphql.autoconfiguration.graphqlwebmvcsecurity.enabled",
                    "org.springframework.boot.graphql.autoconfigure.security.GraphQlWebMvcSecurityAutoConfiguration",
                ),
            )

        private const val EXCLUDE = "spring.autoconfigure.exclude"
    }
}
