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
package com.netflix.graphql.dgs

import com.netflix.graphql.dgs.internal.DataFetcherResultProcessor
import graphql.TrivialDataFetcher
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactories
import graphql.schema.DataFetchingEnvironment
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLFieldDefinition
import org.springframework.context.ApplicationContext

/**
 * Utility wrapper for [GraphQLCodeRegistry.Builder] which provides
 * a consistent registration mechanism of DataFetchers similar to the annotation-based approach.
 * Can be used as a first parameter of a [DgsCodeRegistry] annotated method.
 */
class DgsCodeRegistryBuilder(
    private val dataFetcherResultProcessors: List<DataFetcherResultProcessor>,
    private val graphQLCodeRegistry: GraphQLCodeRegistry.Builder,
    private val ctx: ApplicationContext,
) {
    fun dataFetcher(
        coordinates: FieldCoordinates,
        dataFetcher: DataFetcher<*>,
    ): DgsCodeRegistryBuilder {
        val fetcher =
            if (dataFetcherResultProcessors.isNotEmpty() && dataFetcher !is TrivialDataFetcher) {
                DataFetcherFactories.wrapDataFetcher(dataFetcher) { dfe, result -> convertResult(dfe, result) }
            } else {
                dataFetcher
            }

        graphQLCodeRegistry.dataFetcher(coordinates, fetcher)
        return this
    }

    fun hasDataFetcher(coordinates: FieldCoordinates): Boolean = graphQLCodeRegistry.hasDataFetcher(coordinates)

    fun getDataFetcher(
        coordinates: FieldCoordinates,
        fieldDefinition: GraphQLFieldDefinition,
    ): DataFetcher<*> = graphQLCodeRegistry.getDataFetcher(coordinates, fieldDefinition)

    private fun convertResult(
        dfe: DataFetchingEnvironment,
        result: Any?,
    ): Any? {
        if (result == null) {
            return null
        }
        val processor = dataFetcherResultProcessors.find { it.supportsType(result) } ?: return result
        val env =
            if (dfe is DgsDataFetchingEnvironment) {
                dfe
            } else {
                DgsDataFetchingEnvironment(dfe, ctx)
            }
        return processor.process(result, env)
    }
}
