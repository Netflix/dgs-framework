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

package com.netflix.graphql.dgs.metrics.micrometer.tagging

import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlMetric
import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlTag
import graphql.ExecutionResult
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags

class SimpleGqlOutcomeTagCustomizer : DgsExecutionTagCustomizer, DgsFieldFetchTagCustomizer {

    companion object {
        val OUTCOME_SUCCESS: Tag = Tag.of(GqlTag.OUTCOME.key, "SUCCESS")
        val OUTCOME_ERROR: Tag = Tag.of(GqlTag.OUTCOME.key, "ERROR")
    }

    override fun getExecutionTags(
            parameters: InstrumentationExecutionParameters,
            result: ExecutionResult,
            exception: Throwable?
    ): Iterable<Tag> {
        return if (result.errors.isNotEmpty() || exception != null) {
            Tags.of(OUTCOME_ERROR)
        } else {
            Tags.of(OUTCOME_SUCCESS)
        }
    }

    override fun getFieldFetchTags(
            parameters: InstrumentationFieldFetchParameters,
            error: Throwable?
    ): Iterable<Tag> {
        return if (error != null) {
            Tags.of(OUTCOME_ERROR)
        } else {
            Tags.of(OUTCOME_SUCCESS)
        }
    }
}