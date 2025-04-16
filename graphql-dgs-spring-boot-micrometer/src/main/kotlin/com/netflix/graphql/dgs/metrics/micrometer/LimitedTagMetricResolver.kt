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

import com.netflix.graphql.dgs.Internal
import io.micrometer.core.instrument.Tag
import java.util.*

@Internal
interface LimitedTagMetricResolver {
    fun tags(
        key: String,
        value: String,
    ): Iterable<Tag> = tag(key, value).map { listOf(it) }.orElse(emptyList())

    fun tag(
        key: String,
        value: String,
    ): Optional<Tag>
}
