/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.graphql.dgs.starter

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationListener

class LegacyStarterWarning : ApplicationListener<ApplicationStartedEvent> {
    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(LegacyStarterWarning::class.java)
    }

    override fun onApplicationEvent(event: ApplicationStartedEvent) {
        LOGGER.warn(
            "DEPRECATION WARNING - This project is using the deprecated 'graphql-dgs-spring-boot-starter'. Please switch to 'graphql-dgs-spring-graphql-starter'. For more context: https://netflix.github.io/dgs/spring-graphql-integration",
        )
    }
}
