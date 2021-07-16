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
import graphql.schema.DataFetchingEnvironment
import org.dataloader.BatchLoaderEnvironment

/**
 * Context class that is created per request, and is added to both DataFetchingEnvironment and BatchLoaderEnvironment.
 * Custom data can be added by providing a [DgsCustomContextBuilder].
 */
open class DgsContext(val customContext: Any? = null, val requestData: DgsRequestData?) {

    companion object {
        @JvmStatic
        fun <T> getCustomContext(dgsContext: Any): T {
            @Suppress("UNCHECKED_CAST")
            return when (dgsContext) {
                is DgsContext -> dgsContext.customContext as T
                else -> throw RuntimeException("The context object passed to getCustomContext is not a DgsContext. It is a ${dgsContext::class.java.name} instead.")
            }
        }

        @JvmStatic
        fun <T> getCustomContext(dataFetchingEnvironment: DataFetchingEnvironment): T {
            val dgsContext = dataFetchingEnvironment.getContext<DgsContext>()
            return getCustomContext(dgsContext)
        }

        @JvmStatic
        fun <T> getCustomContext(batchLoaderEnvironment: BatchLoaderEnvironment): T {
            val dgsContext = batchLoaderEnvironment.getContext<DgsContext>()
            return getCustomContext(dgsContext)
        }

        @JvmStatic
        fun getRequestData(dataFetchingEnvironment: DataFetchingEnvironment): DgsRequestData? {
            val dgsContext = dataFetchingEnvironment.getContext<DgsContext>()
            return dgsContext.requestData
        }

        @JvmStatic
        fun getRequestData(batchLoaderEnvironment: BatchLoaderEnvironment): DgsRequestData? {
            val dgsContext = batchLoaderEnvironment.getContext<DgsContext>()
            return dgsContext.requestData
        }
    }
}
