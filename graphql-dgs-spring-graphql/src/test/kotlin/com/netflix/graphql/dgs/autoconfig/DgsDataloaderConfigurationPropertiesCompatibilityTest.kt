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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class DgsDataloaderConfigurationPropertiesCompatibilityTest {
    @Test
    fun `retains Kotlin data class API and semantics`() {
        val properties = DgsDataloaderConfigurationProperties(true, Duration.ofMillis(25))

        assertThat(properties.component1()).isTrue()
        assertThat(properties.component2()).isEqualTo(Duration.ofMillis(25))
        assertThat(properties.copy(false, Duration.ofSeconds(1)))
            .isEqualTo(DgsDataloaderConfigurationProperties(false, Duration.ofSeconds(1)))
        assertThat(properties)
            .isEqualTo(DgsDataloaderConfigurationProperties(true, Duration.ofMillis(25)))
            .hasSameHashCodeAs(DgsDataloaderConfigurationProperties(true, Duration.ofMillis(25)))
            .hasToString("DgsDataloaderConfigurationProperties(tickerModeEnabled=true, scheduleDuration=PT0.025S)")
    }
}
