/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.graphql.dgs.springgraphql

import com.netflix.graphql.dgs.internal.DefaultDgsQueryExecutor
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import org.slf4j.LoggerFactory
import org.springframework.graphql.execution.GraphQlSource

class ReloadableGraphQLSource(
    var graphQlSource: GraphQlSource,
    val dgsGraphQLSourceBuilder: DgsGraphQLSourceBuilder,
    val reloadSchemaIndicator: DefaultDgsQueryExecutor.ReloadSchemaIndicator
) : GraphQlSource {
    override fun graphQl(): GraphQL {
        return getSource().graphQl()
    }

    override fun schema(): GraphQLSchema {
        return getSource().schema()
    }

    private fun getSource(): GraphQlSource {
        if (reloadSchemaIndicator.reloadSchema()) {
            LOGGER.info("Rebuilding GraphQLSource")
            graphQlSource = dgsGraphQLSourceBuilder.reload()
        }

        return graphQlSource
    }

    companion object {
        val LOGGER = LoggerFactory.getLogger(ReloadableGraphQLSource::class.java)
    }
}
