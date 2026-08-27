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

package com.netflix.graphql.dgs.metrics.micrometer;

import com.netflix.spectator.api.patterns.CardinalityLimiters;
import io.micrometer.core.instrument.Tag;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** {@link LimitedTagMetricResolver} backed by Spectator's Cardinality Limiters. */
public class SpectatorLimitedTagMetricResolver implements LimitedTagMetricResolver {
    private final DgsGraphQLMetricsProperties.TagsProperties tagsProperties;
    private final ConcurrentHashMap<String, Function<String, String>> dynamicTags = new ConcurrentHashMap<>();

    public SpectatorLimitedTagMetricResolver(DgsGraphQLMetricsProperties.TagsProperties tagsProperties) {
        this.tagsProperties = tagsProperties;
    }

    @Override
    public Optional<Tag> tag(String key, String value) {
        DgsGraphQLMetricsProperties.CardinalityLimiterProperties prop = tagsProperties.getLimiter();
        Function<String, String> limiter =
                dynamicTags.computeIfAbsent(key, ignored -> resolveCardinalityLimiter(prop));
        return Optional.of(Tag.of(key, limiter.apply(value)));
    }

    private Function<String, String> resolveCardinalityLimiter(
            DgsGraphQLMetricsProperties.CardinalityLimiterProperties properties) {
        return switch (properties.getKind()) {
            case FIRST -> CardinalityLimiters.first(properties.getLimit());
            case FREQUENCY -> CardinalityLimiters.mostFrequent(properties.getLimit());
            case ROLLUP -> CardinalityLimiters.rollup(properties.getLimit());
        };
    }

    @Override
    public String toString() {
        return "SpectatorLimitedTagMetricResolver(tagsProperties=" + tagsProperties + ", dynamicTags=" + dynamicTags + ")";
    }
}
