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

package com.netflix.graphql.dgs.springgraphql.bridge

import com.netflix.graphql.dgs.DgsQueryExecutor
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import org.springframework.graphql.execution.GraphQlSource

class DgsGraphQLSource(private val graphqlSchema: GraphQLSchema, private val dgsQueryExecutor: DgsQueryExecutor) : GraphQlSource {
    override fun graphQl(): GraphQL {
        return dgsQueryExecutor.graphQL()
    }

    override fun schema(): GraphQLSchema {
        return graphqlSchema
    }

}