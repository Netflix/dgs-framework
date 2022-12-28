/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.graphql.dgs.apq.caffeine.internal

import com.netflix.graphql.dgs.Internal
import com.netflix.graphql.dgs.apq.DgsAPQSupportProperties
import com.netflix.graphql.dgs.apq.caffeine.DgsCaffeineAPQSupportProperties
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase

@Internal
@Suppress("unused")
class EnableCaffeineCacheCondition : AnyNestedCondition(ConfigurationPhase.PARSE_CONFIGURATION) {

    @ConditionalOnProperty(
        prefix = DgsAPQSupportProperties.PREFIX,
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = DgsAPQSupportProperties.DEFAULT_ENABLE_APQ
    )
    inner class IsApqEnabledGlobally

    @ConditionalOnProperty(
        prefix = DgsCaffeineAPQSupportProperties.PREFIX,
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = DgsCaffeineAPQSupportProperties.DEFAULT_ENABLED
    )
    inner class IsCaffeineApqEnabled
}
