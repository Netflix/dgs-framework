package com.netflix.graphql.dgs.context

import com.netflix.graphql.dgs.logging.LogEvent
import graphql.schema.DataFetchingEnvironment
import org.dataloader.BatchLoaderEnvironment

/**
 * Context class that is created per request, and is added to both DataFetchingEnvironment and BatchLoaderEnvironment.
 * Custom data can be added by providing a [DgsCustomContextBuilder].
 */
open class DgsContext(val customContext: Any?) {
    val logEvent = LogEvent()

    companion object {
        @JvmStatic
        fun <T> getCustomContext(dgsContext: Any) : T {
            @Suppress("UNCHECKED_CAST")
            return when(dgsContext) {
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
    }
}