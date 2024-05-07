package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.exceptions.DgsException
import com.netflix.graphql.types.errors.ErrorDetail
import com.netflix.graphql.types.errors.ErrorType
import com.netflix.graphql.types.errors.TypedGraphQLError
import graphql.ExecutionResult
import graphql.GraphQLError
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.validation.ValidationError
import java.util.concurrent.CompletableFuture

class ValidationErrorInstrumentation : SimplePerformantInstrumentation() {
    override fun instrumentExecutionResult(executionResult: ExecutionResult?, parameters: InstrumentationExecutionParameters?, state: InstrumentationState?): CompletableFuture<ExecutionResult> {
        if (executionResult != null && executionResult.errors.isNotEmpty()) {
            val newExecutionResult = ExecutionResult.newExecutionResult().from(executionResult)
            val graphqlErrors: MutableList<GraphQLError> = mutableListOf()
            executionResult.errors.filterIsInstance<ValidationError>().forEach { validationError ->
                val graphqlError = TypedGraphQLError
                    .newBuilder()
                    .errorType(ErrorType.BAD_REQUEST)
                    .errorDetail(ErrorDetail.Common.FIELD_NOT_FOUND)
                    .message(validationError.message)
                    .extensions(mapOf(DgsException.EXTENSION_CLASS_KEY to "ValidationError"))
                    .build()
                graphqlErrors.add(graphqlError)
            }
            return CompletableFuture.completedFuture(newExecutionResult.errors(graphqlErrors).build())
        }
        return super.instrumentExecutionResult(executionResult, parameters, state)
    }
}
