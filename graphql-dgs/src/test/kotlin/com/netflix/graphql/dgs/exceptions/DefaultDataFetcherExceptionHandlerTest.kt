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

import com.netflix.graphql.types.errors.ErrorType
import graphql.Scalars.GraphQLString
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.ExecutionStepInfo
import graphql.execution.MergedField
import graphql.execution.ResultPath
import graphql.language.Field
import graphql.language.SourceLocation
import graphql.schema.DataFetchingEnvironmentImpl
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.event.Level
import org.slf4j.spi.NOPLoggingEventBuilder
import org.springframework.security.access.AccessDeniedException
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletionException

class DefaultDataFetcherExceptionHandlerTest {
    private val field =
        MergedField
            .newMergedField(
                Field
                    .newField()
                    .name("bar")
                    .sourceLocation(SourceLocation(5, 5))
                    .build(),
            ).build()
    private val executionStepInfo =
        ExecutionStepInfo
            .newExecutionStepInfo()
            .type(GraphQLString)
            .field(field)
            .path(ResultPath.fromList(listOf("Foo", "bar")))
            .build()
    private val environment =
        DataFetchingEnvironmentImpl
            .newDataFetchingEnvironment()
            .mergedField(field)
            .executionStepInfo(executionStepInfo)
            .build()

    @Test
    fun securityError() {
        val handlerParameters =
            DataFetcherExceptionHandlerParameters
                .newExceptionParameters()
                .dataFetchingEnvironment(environment)
                .exception(AccessDeniedException("Denied"))
                .build()
        val result = DefaultDataFetcherExceptionHandler().handleException(handlerParameters).get()
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo("PERMISSION_DENIED")

        assertThat(result.errors[0].errorType).isEqualTo(ErrorType.PERMISSION_DENIED)
    }

    @Test
    fun normalError() {
        val handlerParameters =
            DataFetcherExceptionHandlerParameters
                .newExceptionParameters()
                .exception(RuntimeException("Something broke"))
                .dataFetchingEnvironment(environment)
                .build()
        val result = DefaultDataFetcherExceptionHandler().handleException(handlerParameters).get()
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo("INTERNAL")

        assertThat(result.errors[0].errorType).isEqualTo(ErrorType.INTERNAL)
        assertThat(result.errors[0].path).isEqualTo(listOf("Foo", "bar"))
        assertThat(result.errors[0].locations).isEqualTo(listOf(SourceLocation(5, 5)))
    }

    @Test
    fun normalErrorWithSpecialCharacterString() {
        val handlerParameters =
            DataFetcherExceptionHandlerParameters
                .newExceptionParameters()
                .exception(RuntimeException("/bgt_budgetingProject/specificPass: not a PassId: bdgt%3Apass%2F3"))
                .dataFetchingEnvironment(environment)
                .build()

        val result = DefaultDataFetcherExceptionHandler().handleException(handlerParameters).get()
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo("INTERNAL")

        assertThat(result.errors[0].errorType).isEqualTo(ErrorType.INTERNAL)
    }

    @Test
    fun entityNotFoundException() {
        val handlerParameters =
            DataFetcherExceptionHandlerParameters
                .newExceptionParameters()
                .exception(DgsEntityNotFoundException("Movie with movieId '1' was not found"))
                .dataFetchingEnvironment(environment)
                .build()

        val result = DefaultDataFetcherExceptionHandler().handleException(handlerParameters).get()
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo("NOT_FOUND")

        assertThat(result.errors[0].errorType).isEqualTo(ErrorType.NOT_FOUND)
    }

    @Test
    fun badRequestException() {
        val handlerParameters =
            DataFetcherExceptionHandlerParameters
                .newExceptionParameters()
                .exception(DgsBadRequestException("Malformed movie request"))
                .dataFetchingEnvironment(environment)
                .build()

        val result = DefaultDataFetcherExceptionHandler().handleException(handlerParameters).get()
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo("BAD_REQUEST")

        assertThat(result.errors[0].errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @Test
    fun `custom DGS exception should return custom error`() {
        val customDgsExceptionMessage = "Studio Search Who"
        val customDgsExceptionType = ErrorType.FAILED_PRECONDITION

        class CustomDgsException : DgsException(message = customDgsExceptionMessage, errorType = customDgsExceptionType)

        val handlerParameters =
            DataFetcherExceptionHandlerParameters
                .newExceptionParameters()
                .exception(CustomDgsException())
                .dataFetchingEnvironment(environment)
                .build()

        val result = DefaultDataFetcherExceptionHandler().handleException(handlerParameters).get()
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo(customDgsExceptionType.name)

        assertThat(result.errors[0].errorType).isEqualTo(customDgsExceptionType)
    }

    @Test
    fun `CompletionException returns wrapped error code`() {
        val completionException =
            CompletionException(
                "com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException: Requested entity not found",
                DgsEntityNotFoundException(),
            )

        val handlerParameters =
            DataFetcherExceptionHandlerParameters
                .newExceptionParameters()
                .exception(completionException)
                .dataFetchingEnvironment(environment)
                .build()

        val result = DefaultDataFetcherExceptionHandler().handleException(handlerParameters).get()
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo("NOT_FOUND")

        assertThat(result.errors[0].errorType).isEqualTo(ErrorType.NOT_FOUND)
    }

    @Test
    fun `DgsException with explicit log level`() {
        val debugLevelException =
            object : DgsException(
                "something went wrong",
                IllegalStateException("something went wrong"),
                ErrorType.UNAVAILABLE,
                Level.DEBUG,
            ) {}

        val defaultLevelException =
            object : DgsException(
                "something went wrong",
                IllegalStateException("something went wrong"),
                ErrorType.UNAVAILABLE,
            ) {}

        // Configure the logger to be a mock so we can check invocations
        val loggerMock = spyk<Logger>()
        every { loggerMock.atLevel(any()) } answers { NOPLoggingEventBuilder.singleton() }

        val mock = spyk<DefaultDataFetcherExceptionHandler>()
        every { mock.getProperty("logger") } answers { loggerMock }

        val handlerParametersForDebugException =
            DataFetcherExceptionHandlerParameters
                .newExceptionParameters()
                .exception(debugLevelException)
                .dataFetchingEnvironment(environment)
                .build()

        val handlerParametersForDefaultException =
            DataFetcherExceptionHandlerParameters
                .newExceptionParameters()
                .exception(defaultLevelException)
                .dataFetchingEnvironment(environment)
                .build()

        // Handle both exceptions
        mock.handleException(handlerParametersForDebugException).get()
        mock.handleException(handlerParametersForDefaultException).get()

        verify { loggerMock.atLevel(Level.DEBUG) }
        verify { loggerMock.atLevel(Level.ERROR) }
        confirmVerified(loggerMock)
    }

    @Test
    fun `unwraps the invocation target exception`() {
        val invocation = InvocationTargetException(IllegalStateException("I'm illegal!"), "Target invocation happened")

        val params =
            DataFetcherExceptionHandlerParameters
                .newExceptionParameters()
                .exception(invocation)
                .dataFetchingEnvironment(environment)
                .build()

        val result = DefaultDataFetcherExceptionHandler().handleException(params).get()

        assertThat(result.errors.size).isEqualTo(1)
        assertThat(result.errors[0].message).containsSubsequence("java.lang.IllegalStateException: I'm illegal!")
    }
}
