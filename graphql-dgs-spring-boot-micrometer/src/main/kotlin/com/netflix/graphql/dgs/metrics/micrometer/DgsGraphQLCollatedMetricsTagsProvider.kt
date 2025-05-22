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

import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsContextualTagCustomizer
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsExecutionTagCustomizer
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsFieldFetchTagCustomizer
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsGraphQLMetricsTagsProvider
import graphql.ExecutionResult
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import io.micrometer.core.instrument.Tag
import java.util.Collections

class DgsGraphQLCollatedMetricsTagsProvider(
    private val contextualTagCustomizer: Collection<DgsContextualTagCustomizer> = Collections.emptyList(),
    private val executionTagCustomizer: Collection<DgsExecutionTagCustomizer> = Collections.emptyList(),
    private val fieldFetchTagCustomizer: Collection<DgsFieldFetchTagCustomizer> = Collections.emptyList(),
) : DgsGraphQLMetricsTagsProvider {
    override fun getContextualTags(): Iterable<Tag> = contextualTagCustomizer.flatMap { it.getContextualTags() }

    override fun getExecutionTags(
        state: DgsGraphQLMetricsInstrumentation.MetricsInstrumentationState,
        parameters: InstrumentationExecutionParameters,
        result: ExecutionResult,
        exception: Throwable?,
    ): Iterable<Tag> = executionTagCustomizer.flatMap { it.getExecutionTags(state, parameters, result, exception) }

    override fun getFieldFetchTags(
        state: DgsGraphQLMetricsInstrumentation.MetricsInstrumentationState,
        parameters: InstrumentationFieldFetchParameters,
        exception: Throwable?,
    ): Iterable<Tag> = fieldFetchTagCustomizer.flatMap { it.getFieldFetchTags(state, parameters, exception) }
}
