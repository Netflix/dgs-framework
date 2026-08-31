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

package com.netflix.graphql.dgs.internal;

import com.netflix.graphql.types.errors.ErrorDetail;
import com.netflix.graphql.types.errors.ErrorType;
import com.netflix.graphql.types.errors.TypedGraphQLError;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.SerializationError;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.validation.ValidationError;
import graphql.validation.ValidationErrorType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GraphQLJavaErrorInstrumentation extends SimplePerformantInstrumentation {
    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(
            ExecutionResult executionResult,
            InstrumentationExecutionParameters parameters,
            InstrumentationState state) {
        if (!executionResult.getErrors().isEmpty()) {
            ExecutionResult.Builder<?> newExecutionResult = ExecutionResult.newExecutionResult().from(executionResult);
            List<GraphQLError> graphqlErrors = new ArrayList<>();

            for (GraphQLError error : executionResult.getErrors()) {
                // put in the classification unless it's already there since graphql-java errors contain this field
                Map<String, Object> extensions =
                        error.getExtensions() != null ? new HashMap<>(error.getExtensions()) : new HashMap<>();
                if (!extensions.containsKey("classification") && error.getErrorType() != null) {
                    extensions.put("classification", error.getErrorType().toSpecification(error));
                }

                if (error.getErrorType() == graphql.ErrorType.ValidationError
                        || error.getErrorType() == graphql.ErrorType.InvalidSyntax
                        || error.getErrorType() == graphql.ErrorType.NullValueInNonNullableField
                        || error.getErrorType() == graphql.ErrorType.OperationNotSupported
                        || error.getErrorType() == graphql.ErrorType.ExecutionAborted) {
                    List<Object> path =
                            error instanceof ValidationError validationError
                                    ? asObjectList(validationError.getQueryPath())
                                    : error.getPath();
                    TypedGraphQLError.Builder graphqlErrorBuilder = TypedGraphQLError
                            .newBadRequestBuilder()
                            .locations(error.getLocations())
                            .path(path)
                            .message(error.getMessage())
                            .extensions(extensions);

                    if (error instanceof ValidationError validationError) {
                        if (validationError.getValidationErrorType() == ValidationErrorType.FieldUndefined) {
                            graphqlErrorBuilder.errorDetail(ErrorDetail.Common.FIELD_NOT_FOUND);
                        } else {
                            graphqlErrorBuilder.errorDetail(ErrorDetail.Common.INVALID_ARGUMENT);
                        }
                    }

                    if (error.getErrorType() == graphql.ErrorType.OperationNotSupported) {
                        graphqlErrorBuilder.errorDetail(ErrorDetail.Common.INVALID_ARGUMENT);
                    }
                    graphqlErrors.add(graphqlErrorBuilder.build());
                } else if (error.getErrorType() == graphql.ErrorType.DataFetchingException) {
                    TypedGraphQLError.Builder graphqlErrorBuilder = TypedGraphQLError
                            .newBuilder()
                            .errorType(ErrorType.INTERNAL)
                            .errorDetail(ErrorDetail.Common.SERVICE_ERROR)
                            .locations(error.getLocations())
                            .message(error.getMessage())
                            .extensions(error.getExtensions());
                    if (error instanceof SerializationError) {
                        graphqlErrorBuilder.errorDetail(ErrorDetail.Common.SERIALIZATION_ERROR);
                    }
                    if (error.getPath() != null) {
                        graphqlErrorBuilder.path(error.getPath());
                    }
                    graphqlErrors.add(graphqlErrorBuilder.build());
                } else {
                    graphqlErrors.add(error);
                }
            }
            return CompletableFuture.completedFuture(newExecutionResult.errors(graphqlErrors).build());
        }
        return super.instrumentExecutionResult(executionResult, parameters, state);
    }

    private static List<Object> asObjectList(List<?> path) {
        return path != null ? List.copyOf(path) : null;
    }
}
