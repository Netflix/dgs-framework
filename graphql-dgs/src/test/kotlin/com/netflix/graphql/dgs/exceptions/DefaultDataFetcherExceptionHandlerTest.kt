package com.netflix.graphql.dgs.exceptions

import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.ExecutionPath
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.BeforeEach
import org.springframework.security.access.AccessDeniedException
import java.lang.RuntimeException

internal class DefaultDataFetcherExceptionHandlerTest {

    @MockK
    lateinit var dataFetcherExceptionHandlerParameters: DataFetcherExceptionHandlerParameters

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        every { dataFetcherExceptionHandlerParameters.path }.returns(ExecutionPath.parse("/Query/test"))

    }

    @Test
    fun securityError() {
        every { dataFetcherExceptionHandlerParameters.exception}.returns(AccessDeniedException("Denied"))

        val result = DefaultDataFetcherExceptionHandler().onException(dataFetcherExceptionHandlerParameters)
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo("PERMISSION_DENIED")

        // We return null here because we don't want graphql-java to write classification field
        assertThat(result.errors[0].errorType).isNull()
    }

    @Test
    fun normalError() {
        every { dataFetcherExceptionHandlerParameters.exception}.returns(RuntimeException("Something broke"))

        val result = DefaultDataFetcherExceptionHandler().onException(dataFetcherExceptionHandlerParameters)
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo("INTERNAL")

        // We return null here because we don't want graphql-java to write classification field
        assertThat(result.errors[0].errorType).isNull()
    }

    @Test
    fun normalErrorWithSpecialCharacterString() {
        every { dataFetcherExceptionHandlerParameters.exception}.returns(RuntimeException("/bgt_budgetingProject/specificPass: not a PassId: bdgt%3Apass%2F3"))

        val result = DefaultDataFetcherExceptionHandler().onException(dataFetcherExceptionHandlerParameters)
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo("INTERNAL")

        // We return null here because we don't want graphql-java to write classification field
        assertThat(result.errors[0].errorType).isNull()
    }

    @Test
    fun entityNotFoundException() {
        every { dataFetcherExceptionHandlerParameters.exception }.returns(DgsEntityNotFoundException("Movie with movieId '1' was not found"))

        val result = DefaultDataFetcherExceptionHandler().onException(dataFetcherExceptionHandlerParameters)
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo("NOT_FOUND")

        // We return null here because we don't want graphql-java to write classification field
        assertThat(result.errors[0].errorType).isNull()
    }
}