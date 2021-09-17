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

/**
 * Default DataFetcherExceptionHandler used by the framework, can be replaced with a custom implementation.
 * The default implementation uses the Common Errors library to return GraphQL errors.
 */
class DefaultDataFetcherExceptionHandler : DataFetcherExceptionHandler {

    override fun onException(handlerParameters: DataFetcherExceptionHandlerParameters?): DataFetcherExceptionHandlerResult {

        val exception = handlerParameters!!.exception
        logger.error("Exception while executing data fetcher for ${handlerParameters.path}: ${exception.message}", exception)

        val graphqlError = if (springSecurityAvailable && isSpringSecurityAccessException(exception)) {
            TypedGraphQLError.newPermissionDeniedBuilder()
                .message("%s: %s", exception::class.java.name, exception.message)
                .path(handlerParameters.path).build()
        } else if (exception is DgsEntityNotFoundException) {
            TypedGraphQLError.newNotFoundBuilder().message("%s: %s", exception::class.java.name, exception.message)
                .path(handlerParameters.path).build()
        } else if (exception is DgsBadRequestException) {
            TypedGraphQLError.newBadRequestBuilder().message("%s: %s", exception::class.java.name, exception.message)
                .path(handlerParameters.path).build()
        } else {
            TypedGraphQLError.newInternalErrorBuilder().message("%s: %s", exception::class.java.name, exception.message)
                .path(handlerParameters.path).build()
        }

        return DataFetcherExceptionHandlerResult.newResult()
            .error(graphqlError)
            .build()
    }

    companion object {

        private val logger: Logger = LoggerFactory.getLogger(DefaultDataFetcherExceptionHandler::class.java)

        private val springSecurityAvailable: Boolean by lazy {
            ClassUtils.isPresent(
                "org.springframework.security.access.AccessDeniedException",
                DefaultDataFetcherExceptionHandler::class.java.classLoader
            )
        }

        private fun isSpringSecurityAccessException(exception: Throwable?): Boolean {
            try {
                return exception is org.springframework.security.access.AccessDeniedException
            } catch (e: Throwable) {
                logger.trace("Unable to verify if {} is a Spring Security's AccessDeniedException.", exception, e)
            }
            return false
        }
    }
}
