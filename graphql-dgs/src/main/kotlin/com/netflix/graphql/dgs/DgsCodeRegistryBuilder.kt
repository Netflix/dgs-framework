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
package com.netflix.graphql.dgs

import com.netflix.graphql.dgs.internal.DataFetcherResultProcessor
import graphql.schema.*

/**
 * Utility wrapper for [GraphQLCodeRegistry.Builder] which provides
 * a consistent registration mechanism of DataFetchers similar to the annotation-based approach.
 * Can be used as a first parameter of a [DgsCodeRegistry] annotated method.
 */
class DgsCodeRegistryBuilder(
    private val dataFetcherResultProcessors: List<DataFetcherResultProcessor>,
    private val graphQLCodeRegistry: GraphQLCodeRegistry.Builder
) {

    fun dataFetcher(coordinates: FieldCoordinates, dataFetcher: DataFetcher<*>?): DgsCodeRegistryBuilder {
        val wrapped = DataFetcherFactories.wrapDataFetcher(dataFetcher) { dfe, result ->
            if (coordinates.typeName == "Subscription") {
                return@wrapDataFetcher result
            }

            result?.let {
                val env = DgsDataFetchingEnvironment(dfe)
                dataFetcherResultProcessors.find { it.supportsType(result) }?.process(result, env) ?: result
            }
        }

        graphQLCodeRegistry.dataFetcher(coordinates, wrapped)
        return this
    }

    fun hasDataFetcher(coordinates: FieldCoordinates?): Boolean {
        return graphQLCodeRegistry.hasDataFetcher(coordinates)
    }

    fun getDataFetcher(coordinates: FieldCoordinates?, fieldDefinition: GraphQLFieldDefinition?): DataFetcher<*> {
        return graphQLCodeRegistry.getDataFetcher(coordinates, fieldDefinition)
    }
}
