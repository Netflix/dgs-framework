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

import com.netflix.graphql.dgs.Internal
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import java.util.*

@Internal
interface LimitedTagMetricResolver {

    fun tags(key: String, value: String): Tags {
        return tag(key, value).map { Tags.of(it) }.orElse(Tags.empty())
    }

    fun tag(key: String, value: String): Optional<Tag>
}
