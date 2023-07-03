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

package com.netflix.graphql.dgs.example.shared.instrumentation;

import com.netflix.graphql.dgs.example.shared.context.ExampleGraphQLContextContributor;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Example Instrumentation that depends on the fact that ContextContributors have already been invoked.
 * Specifically the ExampleGraphQLContextContributor has affected the contents of the GraphQLContext object and has set
 * the CONTRIBUTOR_ENABLED_CONTEXT_KEY.
 */
@Component
public class ExampleInstrumentationDependingOnContextContributor extends SimplePerformantInstrumentation {

    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        GraphQLContext context = parameters.getExecutionInput().getGraphQLContext();
        String contextContributorIndicator = context.get(ExampleGraphQLContextContributor.CONTRIBUTOR_ENABLED_CONTEXT_KEY);
        if (contextContributorIndicator != null) {
            return new InstrumentationState() {
                @Override
                public String toString() {
                    return contextContributorIndicator;
                }
            };
        }
        return super.createState(parameters);
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(
            ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        // skip if the expected property has not been set by context contributor
        if (state == null) {
            return super.instrumentExecutionResult(executionResult, parameters, state);
        }

        // otherwise pass its value via extension to make this testable from a client perspective
        return CompletableFuture.completedFuture(
                ExecutionResultImpl.newExecutionResult()
                        .from(executionResult)
                        .addExtension(ExampleGraphQLContextContributor.CONTRIBUTOR_ENABLED_CONTEXT_KEY, state.toString())
                        .build());
    }

}