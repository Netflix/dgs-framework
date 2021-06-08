/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.graphql.dgs.metrics.micrometer

import com.netflix.spectator.api.patterns.CardinalityLimiters
import io.micrometer.core.instrument.Tag
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/** [LimitedTagMetricResolver] backed by Spectator's Cardinality Limiters. */
internal class SpectatorLimitedTagMetricResolver(
    private val tagsProperties: DgsGraphQLMetricsProperties.TagsProperties
) : LimitedTagMetricResolver {

    private val dynamicTags = ConcurrentHashMap<String, Function<String, String>>()

    override fun tag(key: String, value: String): Optional<Tag> {
        val prop = tagsProperties.limiter
        val limiter = dynamicTags.getOrPut(key) { resolveCardinalityLimiter(prop) }
        return Optional.of(Tag.of(key, limiter.apply(value)))
    }

    private fun resolveCardinalityLimiter(
        properties: DgsGraphQLMetricsProperties.CardinalityLimiterProperties
    ): Function<String, String> {
        return when (properties.kind) {
            DgsGraphQLMetricsProperties.CardinalityLimiterKind.FIRST -> CardinalityLimiters.first(properties.limit)
            DgsGraphQLMetricsProperties.CardinalityLimiterKind.FREQUENCY -> CardinalityLimiters.mostFrequent(properties.limit)
            DgsGraphQLMetricsProperties.CardinalityLimiterKind.ROLLUP -> CardinalityLimiters.rollup(properties.limit)
        }
    }

    override fun toString(): String {
        return "SpectatorLimitedTagMetricResolver(tagsProperties=$tagsProperties, dynamicTags=$dynamicTags)"
    }
}
