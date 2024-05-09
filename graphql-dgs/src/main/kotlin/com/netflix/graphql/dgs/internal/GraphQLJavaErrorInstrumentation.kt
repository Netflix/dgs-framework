package com.netflix.graphql.dgs.internal

import com.netflix.graphql.types.errors.ErrorDetail
import com.netflix.graphql.types.errors.ErrorType
import com.netflix.graphql.types.errors.TypedGraphQLError
import graphql.ExecutionResult
import graphql.GraphQLError
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import java.util.concurrent.CompletableFuture

class GraphQLJavaErrorInstrumentation : SimplePerformantInstrumentation() {
    override fun instrumentExecutionResult(executionResult: ExecutionResult, parameters: InstrumentationExecutionParameters?, state: InstrumentationState?): CompletableFuture<ExecutionResult> {
        if (executionResult.errors.isNotEmpty()) {
            val newExecutionResult = ExecutionResult.newExecutionResult().from(executionResult)
            val graphqlErrors: MutableList<GraphQLError> = mutableListOf()

            executionResult.errors.forEach { error ->
                // put in the classification unless it's already there since graphql-java errors contain this field
                val extensions = (if (error.extensions != null) error.extensions else emptyMap<String, Any>()).toMutableMap()
                if (!extensions.containsKey("classification")) {
                    val errorClassification = error.errorType
                    extensions["classification"] = errorClassification.toSpecification(error)
                }

                if (error.errorType == graphql.ErrorType.ValidationError || error.errorType == graphql.ErrorType.InvalidSyntax ||
                    error.errorType == graphql.ErrorType.NullValueInNonNullableField
                ) {
                    val path = if (error is ValidationError) error.queryPath else error.path
                    val graphqlErrorBuilder = TypedGraphQLError
                        .newBadRequestBuilder()
                        .locations(error.locations)
                        .path(path)
                        .message(error.message)
                        .extensions(extensions)
                    if (error is ValidationError) {
                        if (error.validationErrorType == ValidationErrorType.NullValueForNonNullArgument) {
                            graphqlErrorBuilder.errorDetail(ErrorDetail.Common.INVALID_ARGUMENT)
                        } else if (error.validationErrorType == ValidationErrorType.FieldUndefined) {
                            graphqlErrorBuilder.errorDetail(ErrorDetail.Common.FIELD_NOT_FOUND)
                        }
                    }

                    graphqlErrors.add(graphqlErrorBuilder.build())
                } else if (error.errorType == graphql.ErrorType.OperationNotSupported) {
                    val graphqlErrorBuilder = TypedGraphQLError
                        .newBuilder()
                        .errorType(ErrorType.UNAVAILABLE)
                        .locations(error.locations)
                        .message(error.message)
                        .extensions(error.extensions)
                    graphqlErrors.add(graphqlErrorBuilder.build())
                } else if (error.errorType == graphql.ErrorType.DataFetchingException || error.errorType == graphql.ErrorType.ExecutionAborted) {
                    val graphqlErrorBuilder = TypedGraphQLError
                        .newBuilder()
                        .errorType(ErrorType.UNKNOWN)
                        .locations(error.locations)
                        .message(error.message)
                        .extensions(error.extensions)
                    graphqlErrors.add(graphqlErrorBuilder.build())
                } else {
                    graphqlErrors.add(error)
                }
            }
            return CompletableFuture.completedFuture(newExecutionResult.errors(graphqlErrors).build())
        }
        return super.instrumentExecutionResult(executionResult, parameters, state)
    }
}
