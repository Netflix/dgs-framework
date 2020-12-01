package com.netflix.graphql.dgs

import com.netflix.graphql.dgs.context.DgsContext

/**
 * A DgsContext is created for each request to hold state during that request.
 * This builder abstraction is used to plugin implementations with more or less functionality.
 * Typically not implemented by users. See [com.netflix.graphql.dgs.context.DgsCustomContextBuilder] instead for adding custom state to the context.
 */
interface DgsContextBuilder {
    fun build(): DgsContext
}