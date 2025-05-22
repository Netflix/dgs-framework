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

package com.netflix.graphql.dgs.autoconfig

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration

/**
 * Configuration properties for DGS framework.
 */
@ConfigurationProperties(prefix = "dgs.graphql.dataloader")
data class DgsDataloaderConfigurationProperties(
    @DefaultValue("false") val tickerModeEnabled: Boolean,
    @DefaultValue(DATALOADER_DEFAULT_SCHEDULE_DURATION) val scheduleDuration: Duration,
) {
    companion object {
        const val DATALOADER_DEFAULT_SCHEDULE_DURATION = "10ms"
    }
}
