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

package com.netflix.graphql.dgs.metrics.micrometer;

import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsContextualTagCustomizer;
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsExecutionTagCustomizer;
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsFieldFetchTagCustomizer;
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsGraphQLMetricsTagsProvider;
import graphql.ExecutionResult;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import io.micrometer.core.instrument.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DgsGraphQLCollatedMetricsTagsProvider implements DgsGraphQLMetricsTagsProvider {
    private final Collection<? extends DgsContextualTagCustomizer> contextualTagCustomizer;
    private final Collection<? extends DgsExecutionTagCustomizer> executionTagCustomizer;
    private final Collection<? extends DgsFieldFetchTagCustomizer> fieldFetchTagCustomizer;

    public DgsGraphQLCollatedMetricsTagsProvider(
            @NotNull Collection<? extends DgsContextualTagCustomizer> contextualTagCustomizer,
            @NotNull Collection<? extends DgsExecutionTagCustomizer> executionTagCustomizer,
            @NotNull Collection<? extends DgsFieldFetchTagCustomizer> fieldFetchTagCustomizer) {
        this.contextualTagCustomizer = contextualTagCustomizer;
        this.executionTagCustomizer = executionTagCustomizer;
        this.fieldFetchTagCustomizer = fieldFetchTagCustomizer;
    }

    public DgsGraphQLCollatedMetricsTagsProvider() {
        this(List.of(), List.of(), List.of());
    }

    @NotNull
    @Override
    public Iterable<Tag> getContextualTags() {
        List<Tag> tags = new ArrayList<>();
        for (DgsContextualTagCustomizer customizer : contextualTagCustomizer) {
            customizer.getContextualTags().forEach(tags::add);
        }
        return tags;
    }

    @NotNull
    @Override
    public Iterable<Tag> getExecutionTags(
            @NotNull DgsGraphQLMetricsInstrumentation.MetricsInstrumentationState state,
            @NotNull InstrumentationExecutionParameters parameters,
            @NotNull ExecutionResult result,
            @Nullable Throwable exception) {
        List<Tag> tags = new ArrayList<>();
        for (DgsExecutionTagCustomizer customizer : executionTagCustomizer) {
            customizer.getExecutionTags(state, parameters, result, exception).forEach(tags::add);
        }
        return tags;
    }

    @NotNull
    @Override
    public Iterable<Tag> getFieldFetchTags(
            @NotNull DgsGraphQLMetricsInstrumentation.MetricsInstrumentationState state,
            @NotNull InstrumentationFieldFetchParameters parameters,
            @Nullable Throwable exception) {
        List<Tag> tags = new ArrayList<>();
        for (DgsFieldFetchTagCustomizer customizer : fieldFetchTagCustomizer) {
            customizer.getFieldFetchTags(state, parameters, exception).forEach(tags::add);
        }
        return tags;
    }
}
