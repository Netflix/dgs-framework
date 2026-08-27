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

package com.netflix.graphql.dgs.exceptions;

import com.netflix.graphql.types.errors.TypedGraphQLError;
import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Default DataFetcherExceptionHandler used by the framework, can be replaced with a custom implementation.
 * The default implementation uses the Common Errors library to return GraphQL errors.
 */
public class DefaultDataFetcherExceptionHandler implements DataFetcherExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDataFetcherExceptionHandler.class);

    private static final boolean SPRING_SECURITY_AVAILABLE = ClassUtils.isPresent(
            "org.springframework.security.access.AccessDeniedException",
            DefaultDataFetcherExceptionHandler.class.getClassLoader());

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
            DataFetcherExceptionHandlerParameters handlerParameters) {
        return CompletableFuture.completedFuture(doHandleException(handlerParameters));
    }

    private DataFetcherExceptionHandlerResult doHandleException(
            DataFetcherExceptionHandlerParameters handlerParameters) {
        Throwable exception = unwrapCompletionException(handlerParameters.getException());

        GraphQLError graphqlError;
        if (exception instanceof DgsException dgsException) {
            graphqlError = dgsException.toGraphQlError(handlerParameters.getPath());
        } else {
            TypedGraphQLError.Builder builder =
                    SPRING_SECURITY_AVAILABLE && isSpringSecurityAccessException(exception)
                            ? TypedGraphQLError.newPermissionDeniedBuilder()
                            : TypedGraphQLError.newInternalErrorBuilder();
            builder
                    .message(exception.getClass().getName() + ": " + exception.getMessage())
                    .path(handlerParameters.getPath());
            if (handlerParameters.getSourceLocation() != null) {
                builder.location(handlerParameters.getSourceLocation());
            }
            graphqlError = builder.build();
        }

        logException(handlerParameters, graphqlError, exception);

        return DataFetcherExceptionHandlerResult
                .newResult()
                .error(graphqlError)
                .build();
    }

    protected void logException(
            DataFetcherExceptionHandlerParameters handlerParameters, GraphQLError error, Throwable exception) {
        Level logLevel = exception instanceof DgsException dgsException ? dgsException.getLogLevel() : Level.ERROR;

        getLogger()
                .atLevel(logLevel)
                .setCause(exception)
                .log(
                        "Exception while executing data fetcher for {}: {}",
                        handlerParameters.getPath(),
                        exception.getMessage());
    }

    private Throwable unwrapCompletionException(Throwable e) {
        if (e instanceof CompletionException) {
            return unwrapCompletionException(e.getCause() != null ? e.getCause() : e);
        }
        if (e instanceof InvocationTargetException invocationTargetException) {
            return unwrapCompletionException(invocationTargetException.getTargetException());
        }
        return e;
    }

    public Logger getLogger() {
        return LOGGER;
    }

    private static boolean isSpringSecurityAccessException(Throwable exception) {
        try {
            return exception instanceof org.springframework.security.access.AccessDeniedException;
        } catch (Throwable e) {
            LOGGER.trace("Unable to verify if {} is a Spring Security's AccessDeniedException.", exception, e);
        }
        return false;
    }
}
