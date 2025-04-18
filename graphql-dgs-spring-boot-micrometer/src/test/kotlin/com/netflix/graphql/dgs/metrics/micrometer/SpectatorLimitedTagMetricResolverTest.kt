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

package com.netflix.graphql.dgs.metrics.micrometer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.function.Consumer

internal class SpectatorLimitedTagMetricResolverTest {
    companion object {
        private const val DEFAULT_LIMIT = 3
    }

    private val tagProps =
        DgsGraphQLMetricsProperties.TagsProperties(
            limiter =
                DgsGraphQLMetricsProperties.CardinalityLimiterProperties(
                    kind = DgsGraphQLMetricsProperties.CardinalityLimiterKind.FIRST,
                    limit = DEFAULT_LIMIT,
                ),
        )

    @Test
    fun `Limits cardinality per tag`() {
        val resolver = SpectatorLimitedTagMetricResolver(tagProps)

        assertTagCardinality(resolver, "foo")
        assertTagCardinality(resolver, "bar")
    }

    private fun assertTagCardinality(
        resolver: SpectatorLimitedTagMetricResolver,
        name: String,
        limit: Int = DEFAULT_LIMIT,
    ) {
        val list = (0..limit).map { resolver.tag(name, it.toString()) }

        assertThat(list).isNotEmpty
        assertThat(list.map { it.get().key }.distinct())
            .singleElement()
            .satisfies(Consumer { assertThat(it).isEqualTo(name) })
        assertThat(list.mapIndexed { a, b -> a to b.get().value })
            .allSatisfy(
                Consumer { (i, v) ->
                    if (i < limit) {
                        assertThat(v).isEqualTo(i.toString())
                    } else {
                        assertThat(v).isEqualTo("--others--")
                    }
                },
            )
    }
}
