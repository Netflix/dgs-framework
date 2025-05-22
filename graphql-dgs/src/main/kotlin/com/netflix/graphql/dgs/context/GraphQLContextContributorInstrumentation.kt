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

package com.netflix.graphql.dgs.context

import graphql.GraphQLContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters

/**
 * Instrumentation that allows GraphQLContextContributor's to contribute to values stored in the GraphQLContext object.
 * For each contributor, invoke the GraphQLContextContributor's contribute method, and then put the resulting contents
 * of the intermediate GraphQLContext into the existing GraphQLContext.
 *
 * @see com.netflix.graphql.dgs.context.GraphQLContextContributor.contribute()
 */
class GraphQLContextContributorInstrumentation(
    private val graphQLContextContributors: List<GraphQLContextContributor>,
) : SimplePerformantInstrumentation() {
    /**
     * createState is the very first method invoked in an Instrumentation, and thus is where this logic is placed to
     * contribute to the GraphQLContext as early as possible.
     */
    override fun createState(parameters: InstrumentationCreateStateParameters): InstrumentationState? {
        val graphqlContext = parameters.executionInput.graphQLContext
        if (graphqlContext != null && graphQLContextContributors.isNotEmpty()) {
            val extensions = parameters.executionInput.extensions
            val requestData = DgsContext.from(graphqlContext).requestData
            val builderForContributors = GraphQLContext.newContext()
            graphQLContextContributors.forEach { it.contribute(builderForContributors, extensions, requestData) }
            graphqlContext.putAll(builderForContributors)
        }
        return super.createState(parameters)
    }
}
