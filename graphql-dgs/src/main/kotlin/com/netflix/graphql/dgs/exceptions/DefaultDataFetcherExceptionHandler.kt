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
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ClassUtils
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/**
 * Default DataFetcherExceptionHandler used by the framework, can be replaced with a custom implementation.
 * The default implementation uses the Common Errors library to return GraphQL errors.
 */
class DefaultDataFetcherExceptionHandler : DataFetcherExceptionHandler {

    @Deprecated("Deprecated in GraphQL Java", replaceWith = ReplaceWith("handleException(handlerParameters)"))
    override fun onException(handlerParameters: DataFetcherExceptionHandlerParameters): DataFetcherExceptionHandlerResult {
        return doHandleException(handlerParameters)
    }

    override fun handleException(handlerParameters: DataFetcherExceptionHandlerParameters): CompletableFuture<DataFetcherExceptionHandlerResult> {
        return CompletableFuture.completedFuture(doHandleException(handlerParameters))
    }

    private fun doHandleException(handlerParameters: DataFetcherExceptionHandlerParameters): DataFetcherExceptionHandlerResult {
        val exception = unwrapCompletionException(handlerParameters.exception)
        logger.error(
            "Exception while executing data fetcher for {}: {}",
            handlerParameters.path,
            exception.message,
            exception
        )

        val graphqlError = when (exception) {
            is DgsException -> exception.toGraphQlError(handlerParameters.path)
            else -> when {
                springSecurityAvailable && isSpringSecurityAccessException(exception) -> TypedGraphQLError.newPermissionDeniedBuilder()
                else -> TypedGraphQLError.newInternalErrorBuilder()
            }.message("${exception::class.java.name}: ${exception.message}")
                .path(handlerParameters.path)
                .build()
        }

        return DataFetcherExceptionHandlerResult.newResult()
            .error(graphqlError)
            .build()
    }

    private fun unwrapCompletionException(e: Throwable): Throwable {
        return if (e is CompletionException && e.cause != null) e.cause!! else e
    }

    companion object {

        private val logger: Logger = LoggerFactory.getLogger(DefaultDataFetcherExceptionHandler::class.java)
        private val springSecurityAvailable = ClassUtils.isPresent(
            "org.springframework.security.access.AccessDeniedException",
            DefaultDataFetcherExceptionHandler::class.java.classLoader
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
