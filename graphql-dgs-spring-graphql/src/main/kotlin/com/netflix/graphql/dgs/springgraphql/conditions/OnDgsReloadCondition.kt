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

package com.netflix.graphql.dgs.springgraphql.conditions

import org.springframework.boot.autoconfigure.condition.ConditionOutcome
import org.springframework.boot.autoconfigure.condition.SpringBootCondition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.env.Environment
import org.springframework.core.type.AnnotatedTypeMetadata
import kotlin.collections.contains

class OnDgsReloadCondition : SpringBootCondition() {
    companion object {
        /**
         * `true`, if the _DGS Reload flag_ is enabled.
         */
        fun evaluate(environment: Environment): Boolean {
            val isLaptopProfile = environment.activeProfiles.contains("laptop")
            val reloadEnabled = environment.getProperty("dgs.reload", Boolean::class.java, isLaptopProfile)
            return reloadEnabled
        }
    }

    override fun getMatchOutcome(
        context: ConditionContext?,
        metadata: AnnotatedTypeMetadata?,
    ): ConditionOutcome? {
        val environment = context!!.environment
        val reloadEnabled = evaluate(environment)
        return if (reloadEnabled) {
            ConditionOutcome.match("DgsReload enabled.")
        } else {
            ConditionOutcome.noMatch("DgsReload disabled")
        }
    }
}
