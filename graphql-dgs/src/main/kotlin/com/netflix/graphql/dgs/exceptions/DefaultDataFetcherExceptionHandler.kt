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

package com.netflix.graphql.dgs.exceptions

import com.netflix.graphql.types.errors.TypedGraphQLError
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.springframework.util.ClassUtils
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/**
 * Default DataFetcherExceptionHandler used by the framework, can be replaced with a custom implementation.
 * The default implementation uses the Common Errors library to return GraphQL errors.
 */
open class DefaultDataFetcherExceptionHandler : DataFetcherExceptionHandler {
    override fun handleException(
        handlerParameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> = CompletableFuture.completedFuture(doHandleException(handlerParameters))

    private fun doHandleException(handlerParameters: DataFetcherExceptionHandlerParameters): DataFetcherExceptionHandlerResult {
        val exception = unwrapCompletionException(handlerParameters.exception)

        val graphqlError =
            when (exception) {
                is DgsException -> exception.toGraphQlError(path = handlerParameters.path)
                else -> {
                    val builder =
                        when {
                            springSecurityAvailable &&
                                isSpringSecurityAccessException(
                                    exception,
                                ) -> TypedGraphQLError.newPermissionDeniedBuilder()

                            else -> TypedGraphQLError.newInternalErrorBuilder()
                        }
                    builder
                        .message("${exception::class.java.name}: ${exception.message}")
                        .path(handlerParameters.path)
                    handlerParameters.sourceLocation?.let { builder.location(it) }
                    builder.build()
                }
            }

        logException(handlerParameters, graphqlError, exception)

        return DataFetcherExceptionHandlerResult
            .newResult()
            .error(graphqlError)
            .build()
    }

    protected open fun logException(
        handlerParameters: DataFetcherExceptionHandlerParameters,
        error: GraphQLError,
        exception: Throwable,
    ) {
        val logLevel = if (exception is DgsException) exception.logLevel else Level.ERROR

        logger.atLevel(logLevel).setCause(exception).log(
            "Exception while executing data fetcher for {}: {}",
            handlerParameters.path,
            exception.message,
        )
    }

    private fun unwrapCompletionException(e: Throwable): Throwable =
        when (e) {
            is CompletionException -> unwrapCompletionException(e.cause ?: e)
            is InvocationTargetException -> unwrapCompletionException(e.targetException)
            else -> e
        }

    protected val logger: Logger get() = DefaultDataFetcherExceptionHandler.logger

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DefaultDataFetcherExceptionHandler::class.java)
        private val springSecurityAvailable =
            ClassUtils.isPresent(
                "org.springframework.security.access.AccessDeniedException",
                DefaultDataFetcherExceptionHandler::class.java.classLoader,
            )

        private fun isSpringSecurityAccessException(exception: Throwable): Boolean {
            try {
                return exception is org.springframework.security.access.AccessDeniedException
            } catch (e: Throwable) {
                logger.trace("Unable to verify if {} is a Spring Security's AccessDeniedException.", exception, e)
            }
            return false
        }
    }
}
