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

package com.netflix.graphql.dgs

import graphql.schema.DataFetcherFactories
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaDirectiveWiringEnvironment

/**
 *  An `@DgsDirective` example for test purpose.
 */
@DgsDirective(name = "uppercase")
class UppercaseDirective : SchemaDirectiveWiring {

    override fun onField(env: SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition>): GraphQLFieldDefinition {

        val field = env.element
        val parentType = env.fieldsContainer

        val originalDataFetcher = env.codeRegistry.getDataFetcher(parentType, field)
        val dataFetcher = DataFetcherFactories.wrapDataFetcher(originalDataFetcher) { _, value ->
            if (value is String) value.toUpperCase() else value
        }

        env.codeRegistry.dataFetcher(parentType, field, dataFetcher)
        return field
    }
}
