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

package com.netflix.graphql.dgs.autoconfig;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Objects;

/** Configuration properties for DGS framework. */
@ConfigurationProperties(prefix = "dgs.graphql.dataloader")
public class DgsDataloaderConfigurationProperties {
    @NotNull
    public static final String DATALOADER_DEFAULT_SCHEDULE_DURATION = "10ms";

    private final boolean tickerModeEnabled;
    private final Duration scheduleDuration;

    public DgsDataloaderConfigurationProperties(
            @DefaultValue("false") boolean tickerModeEnabled,
            @NotNull @DefaultValue(DATALOADER_DEFAULT_SCHEDULE_DURATION) Duration scheduleDuration) {
        this.tickerModeEnabled = tickerModeEnabled;
        this.scheduleDuration = Objects.requireNonNull(scheduleDuration, "scheduleDuration");
    }

    public boolean isTickerModeEnabled() {
        return tickerModeEnabled;
    }

    /** Retained for compatibility with the former Kotlin property getter. */
    public boolean getTickerModeEnabled() {
        return tickerModeEnabled;
    }

    @NotNull
    public Duration getScheduleDuration() {
        return scheduleDuration;
    }

    public boolean component1() {
        return tickerModeEnabled;
    }

    @NotNull
    public Duration component2() {
        return scheduleDuration;
    }

    @NotNull
    public DgsDataloaderConfigurationProperties copy(boolean tickerModeEnabled, @NotNull Duration scheduleDuration) {
        return new DgsDataloaderConfigurationProperties(tickerModeEnabled, scheduleDuration);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof DgsDataloaderConfigurationProperties that
                && tickerModeEnabled == that.tickerModeEnabled
                && scheduleDuration.equals(that.scheduleDuration);
    }

    @Override
    public int hashCode() {
        return 31 * Boolean.hashCode(tickerModeEnabled) + scheduleDuration.hashCode();
    }

    @NotNull
    @Override
    public String toString() {
        return "DgsDataloaderConfigurationProperties(tickerModeEnabled=" + tickerModeEnabled
                + ", scheduleDuration=" + scheduleDuration + ")";
    }
}
