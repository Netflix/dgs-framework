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

package com.netflix.graphql.dgs.metrics.micrometer.tagging

import com.netflix.graphql.dgs.metrics.DgsMetrics.CommonTags.*
import com.netflix.graphql.dgs.metrics.micrometer.DgsGraphQLMetricsInstrumentation
import graphql.ExecutionResult
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import io.micrometer.core.instrument.Tag

class SimpleGqlOutcomeTagCustomizer :
    DgsExecutionTagCustomizer,
    DgsFieldFetchTagCustomizer {
    override fun getExecutionTags(
        state: DgsGraphQLMetricsInstrumentation.MetricsInstrumentationState,
        parameters: InstrumentationExecutionParameters,
        result: ExecutionResult,
        exception: Throwable?,
    ): Iterable<Tag> =
        if (result.errors.isNotEmpty() || exception != null) {
            listOf(FAILURE.tag)
        } else {
            listOf(SUCCESS.tag)
        }

    override fun getFieldFetchTags(
        state: DgsGraphQLMetricsInstrumentation.MetricsInstrumentationState,
        parameters: InstrumentationFieldFetchParameters,
        error: Throwable?,
    ): Iterable<Tag> =
        if (error == null) {
            listOf(SUCCESS.tag)
        } else {
            listOf(FAILURE.tag)
        }
}
