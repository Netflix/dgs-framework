package com.netflix.graphql.dgs.context

/**
 * When a bean implementing this interface is found, the framework will call the [build] method for every request.
 * The result of the [build] method is placed on the [DgsContext] and can be retrieved with [DgsContext.customContext]
 * or with one of the static methods on [DgsContext] given a DataFetchingEnvironment or batchLoaderEnvironment.
 */
interface DgsCustomContextBuilder<T> {
    fun build(): T
}