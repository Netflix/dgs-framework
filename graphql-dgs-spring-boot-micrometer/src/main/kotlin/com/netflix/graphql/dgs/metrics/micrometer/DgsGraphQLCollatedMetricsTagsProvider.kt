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

import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsContextualTagCustomizer
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsExecutionTagCustomizer
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsFieldFetchTagCustomizer
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsGraphQLMetricsTagsProvider
import graphql.ExecutionResult
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags

internal class DgsGraphQLCollatedMetricsTagsProvider(
    private val contextualTagCustomizer: Collection<DgsContextualTagCustomizer>,
    private val executionTagCustomizer: Collection<DgsExecutionTagCustomizer>,
    private val fieldFetchTagCustomizer: Collection<DgsFieldFetchTagCustomizer>
) : DgsGraphQLMetricsTagsProvider {

    override fun getContextualTags(): Iterable<Tag> {
        return contextualTagCustomizer.stream().map { it.getContextualTags() }
            .collect(Tags::empty, { a, b -> a.and(b) }, { a, b -> a.and(b) })
    }

    override fun getExecutionTags(
        parameters: InstrumentationExecutionParameters,
        result: ExecutionResult,
        exception: Throwable?
    ): Iterable<Tag> {
        return executionTagCustomizer
            .stream()
            .map { it.getExecutionTags(parameters, result, exception) }
            .reduce(Tags.empty(), { a, b -> a.and(b) }, { a, b -> a.and(b) })
    }

    override fun getFieldFetchTags(parameters: InstrumentationFieldFetchParameters, error: Throwable?): Iterable<Tag> {
        return fieldFetchTagCustomizer
            .stream()
            .map { it.getFieldFetchTags(parameters, error) }
            .reduce(Tags.empty(), { a, b -> a.and(b) }, { a, b -> a.and(b) })
    }
}
