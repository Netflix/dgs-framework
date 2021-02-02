/*
 * Copyright 2020 Netflix, Inc.
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

/**
 * Default DataFetcherExceptionHandler used by the framework, can be replaced with a custom implementation.
 * The default implementation uses the Common Errors library to return GraphQL errors.
 */
class DefaultDataFetcherExceptionHandler : DataFetcherExceptionHandler {
    private val logger: Logger = LoggerFactory.getLogger(DefaultDataFetcherExceptionHandler::class.java)

    override fun onException(handlerParameters: DataFetcherExceptionHandlerParameters?): DataFetcherExceptionHandlerResult {

        val exception = handlerParameters!!.exception
        logger.error("Exception while executing data fetcher for ${handlerParameters.path}: ${exception.message}", exception)

        val springSecurityAvailable = try {
            Class.forName("org.springframework.security.access.AccessDeniedException")
            true
        } catch (ex: ClassNotFoundException) { false }

        val graphqlError = if(springSecurityAvailable && exception is org.springframework.security.access.AccessDeniedException) {
            TypedGraphQLError.PERMISSION_DENIED.message("%s: %s", exception::class.java.name, exception.message)
                    .path(handlerParameters.path).build()
        } else if(exception is DgsEntityNotFoundException) {
            TypedGraphQLError.NOT_FOUND.message("%s: %s", exception::class.java.name, exception.message)
                    .path(handlerParameters.path).build()
        } else if(exception is DgsBadRequestException) {
            TypedGraphQLError.BAD_REQUEST.message("%s: %s", exception::class.java.name, exception.message)
                .path(handlerParameters.path).build()
        } else {
            TypedGraphQLError.INTERNAL.message("%s: %s", exception::class.java.name, exception.message)
                    .path(handlerParameters.path).build()
        }

        return DataFetcherExceptionHandlerResult.newResult()
                .error(graphqlError)
                .build()
    }
}