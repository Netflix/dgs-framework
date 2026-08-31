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

package com.netflix.graphql.dgs.context;

import graphql.GraphQLContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;

import java.util.List;
import java.util.Map;

/**
 * Instrumentation that allows GraphQLContextContributor's to contribute to values stored in the GraphQLContext object.
 * For each contributor, invoke the GraphQLContextContributor's contribute method, and then put the resulting contents
 * of the intermediate GraphQLContext into the existing GraphQLContext.
 *
 * @see GraphQLContextContributor#contribute
 */
public class GraphQLContextContributorInstrumentation extends SimplePerformantInstrumentation {
    private final List<GraphQLContextContributor> graphQLContextContributors;

    public GraphQLContextContributorInstrumentation(List<GraphQLContextContributor> graphQLContextContributors) {
        this.graphQLContextContributors = graphQLContextContributors;
    }

    /**
     * createState is the very first method invoked in an Instrumentation, and thus is where this logic is placed to
     * contribute to the GraphQLContext as early as possible.
     */
    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        GraphQLContext graphqlContext = parameters.getExecutionInput().getGraphQLContext();
        if (!graphQLContextContributors.isEmpty()) {
            Map<String, Object> extensions = parameters.getExecutionInput().getExtensions();
            var requestData = DgsContext.from(graphqlContext).getRequestData();
            GraphQLContext.Builder builderForContributors = GraphQLContext.newContext();
            graphQLContextContributors.forEach(
                    contributor -> contributor.contribute(builderForContributors, extensions, requestData));
            graphqlContext.putAll(builderForContributors);
        }
        return super.createState(parameters);
    }
}
