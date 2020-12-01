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
        }
        else {
            TypedGraphQLError.INTERNAL.message("%s: %s", exception::class.java.name, exception.message)
                    .path(handlerParameters.path).build()
        }

        return DataFetcherExceptionHandlerResult.newResult()
                .error(graphqlError)
                .build()
    }
}