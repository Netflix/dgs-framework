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

package com.netflix.graphql.dgs.context

import graphql.GraphQLContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters

class GraphQLContextContributorInstrumentation(private val graphQLContextContributors: List<GraphQLContextContributor>) :
    SimpleInstrumentation() {

    override fun createState(parameters: InstrumentationCreateStateParameters?): InstrumentationState? {
        var graphqlContext = parameters?.executionInput?.graphQLContext
        if (graphqlContext != null && graphQLContextContributors.iterator().hasNext()) {
            val extensions = parameters?.executionInput?.extensions
            val requestData = DgsContext.from(graphqlContext).requestData
            val builderForContributors = GraphQLContext.newContext()
            graphQLContextContributors.forEach() { it.contribute(builderForContributors, extensions, requestData) }
            graphqlContext.putAll(builderForContributors)
        }

        return super.createState(parameters)
    }
}
