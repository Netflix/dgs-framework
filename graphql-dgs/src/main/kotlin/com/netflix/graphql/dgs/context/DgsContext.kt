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

package com.netflix.graphql.dgs.context

import com.netflix.graphql.dgs.internal.DgsRequestData
import graphql.ExecutionInput
import graphql.GraphQLContext
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.schema.DataFetchingEnvironment
import org.dataloader.BatchLoaderEnvironment
import java.util.function.Consumer

/**
 * Context class that is created per request, and is added to both DataFetchingEnvironment and BatchLoaderEnvironment.
 * Custom data can be added by providing a [DgsCustomContextBuilder].
 */
open class DgsContext(val customContext: Any? = null, val requestData: DgsRequestData?) : Consumer<GraphQLContext.Builder> {

    private enum class GraphQLContextKey { DGS_CONTEXT_KEY }

    companion object {
        @JvmStatic
        fun from(graphQLContext: GraphQLContext): DgsContext {
            return graphQLContext[GraphQLContextKey.DGS_CONTEXT_KEY]
        }

        @JvmStatic
        fun from(dfe: DataFetchingEnvironment): DgsContext {
            return from(dfe.graphQlContext)
        }

        @JvmStatic
        fun from(ei: ExecutionInput): DgsContext {
            return from(ei.graphQLContext)
        }

        @JvmStatic
        fun from(p: InstrumentationCreateStateParameters): DgsContext {
            return from(p.executionInput.graphQLContext)
        }

        @JvmStatic
        fun from(p: InstrumentationExecuteOperationParameters): DgsContext {
            return from(p.executionContext.graphQLContext)
        }

        @JvmStatic
        fun from(p: InstrumentationExecutionParameters): DgsContext {
            return from(p.graphQLContext)
        }

        @JvmStatic
        fun from(p: InstrumentationExecutionStrategyParameters): DgsContext {
            return from(p.executionContext.graphQLContext)
        }

        @JvmStatic
        fun from(p: InstrumentationFieldCompleteParameters): DgsContext {
            return from(p.executionContext.graphQLContext)
        }

        @JvmStatic
        fun from(p: InstrumentationFieldFetchParameters): DgsContext {
            return from(p.executionContext.graphQLContext)
        }

        @JvmStatic
        fun from(p: InstrumentationFieldParameters): DgsContext {
            return from(p.executionContext.graphQLContext)
        }

        @JvmStatic
        fun from(p: InstrumentationValidationParameters): DgsContext {
            return from(p.graphQLContext)
        }

        @JvmStatic
        fun <T> getCustomContext(context: Any): T {
            @Suppress("UNCHECKED_CAST")
            return when (context) {
                is DgsContext -> context.customContext as T
                is GraphQLContext -> getCustomContext(from(context))
                else -> throw RuntimeException("The context object passed to getCustomContext is not a DgsContext. It is a ${context::class.java.name} instead.")
            }
        }

        @JvmStatic
        fun <T> getCustomContext(dataFetchingEnvironment: DataFetchingEnvironment): T {
            val dgsContext = from(dataFetchingEnvironment)
            return getCustomContext(dgsContext)
        }

        @JvmStatic
        fun <T> getCustomContext(batchLoaderEnvironment: BatchLoaderEnvironment): T {
            val dgsContext = batchLoaderEnvironment.getContext<GraphQLContext>()
            return getCustomContext(dgsContext)
        }

        @JvmStatic
        fun getRequestData(dataFetchingEnvironment: DataFetchingEnvironment): DgsRequestData? {
            val dgsContext = from(dataFetchingEnvironment)
            return dgsContext.requestData
        }

        @JvmStatic
        fun getRequestData(batchLoaderEnvironment: BatchLoaderEnvironment): DgsRequestData? {
            val dgsContext = from(batchLoaderEnvironment.getContext<GraphQLContext>())
            return dgsContext.requestData
        }
    }

    override fun accept(contextBuilder: GraphQLContext.Builder) {
        contextBuilder.put(GraphQLContextKey.DGS_CONTEXT_KEY, this)
    }
}
