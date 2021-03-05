package com.netflix.graphql.dgs.metrics.micrometer

import com.netflix.graphql.dgs.support.Unstable
import graphql.ExecutionResult

@FunctionalInterface
@Unstable(message = "The usage of this interface is discouraged, its API will most likely change.")
fun interface DgsGraphQLMetricsExecutionEmitter {
    fun emit(result: ExecutionResult, exc: Throwable?)
}
