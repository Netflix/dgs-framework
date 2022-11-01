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
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.ResultPath
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException

internal class DefaultDataFetcherExceptionHandlerTest {

    @MockK
    lateinit var dataFetcherExceptionHandlerParameters: DataFetcherExceptionHandlerParameters

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        every { dataFetcherExceptionHandlerParameters.path }.returns(ResultPath.parse("/Query/test"))
    }

    @Test
    fun securityError() {
        every { dataFetcherExceptionHandlerParameters.exception }.returns(AccessDeniedException("Denied"))

        val result = DefaultDataFetcherExceptionHandler().handleException(dataFetcherExceptionHandlerParameters).get()
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo("PERMISSION_DENIED")

        // We return null here because we don't want graphql-java to write classification field
        assertThat(result.errors[0].errorType).isNull()
    }

    @Test
    fun normalError() {
        every { dataFetcherExceptionHandlerParameters.exception }.returns(RuntimeException("Something broke"))

        val result = DefaultDataFetcherExceptionHandler().handleException(dataFetcherExceptionHandlerParameters).get()
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo("INTERNAL")

        // We return null here because we don't want graphql-java to write classification field
        assertThat(result.errors[0].errorType).isNull()
    }

    @Test
    fun normalErrorWithSpecialCharacterString() {
        every { dataFetcherExceptionHandlerParameters.exception }.returns(RuntimeException("/bgt_budgetingProject/specificPass: not a PassId: bdgt%3Apass%2F3"))

        val result = DefaultDataFetcherExceptionHandler().handleException(dataFetcherExceptionHandlerParameters).get()
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo("INTERNAL")

        // We return null here because we don't want graphql-java to write classification field
        assertThat(result.errors[0].errorType).isNull()
    }

    @Test
    fun entityNotFoundException() {
        every { dataFetcherExceptionHandlerParameters.exception }.returns(DgsEntityNotFoundException("Movie with movieId '1' was not found"))

        val result = DefaultDataFetcherExceptionHandler().handleException(dataFetcherExceptionHandlerParameters).get()
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo("NOT_FOUND")

        // We return null here because we don't want graphql-java to write classification field
        assertThat(result.errors[0].errorType).isNull()
    }

    @Test
    fun badRequestException() {
        every { dataFetcherExceptionHandlerParameters.exception }.returns(DgsBadRequestException("Malformed movie request"))

        val result = DefaultDataFetcherExceptionHandler().handleException(dataFetcherExceptionHandlerParameters).get()
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo("BAD_REQUEST")

        // We return null here because we don't want graphql-java to write classification field
        assertThat(result.errors[0].errorType).isNull()
    }

    @Test
    fun `custom DGS exception should return custom error`() {
        val customDgsExceptionMessage = "Studio Search Who"
        val customDgsExceptionType = ErrorType.FAILED_PRECONDITION
        class CustomDgsException() :
            DgsException(message = customDgsExceptionMessage, errorType = customDgsExceptionType)

        every { dataFetcherExceptionHandlerParameters.exception }.returns(CustomDgsException())

        val result = DefaultDataFetcherExceptionHandler().handleException(dataFetcherExceptionHandlerParameters).get()
        assertThat(result.errors.size).isEqualTo(1)

        val extensions = result.errors[0].extensions
        assertThat(extensions["errorType"]).isEqualTo(customDgsExceptionType.name)

        // We return null here because we don't want graphql-java to write classification field
        assertThat(result.errors[0].errorType).isNull()
    }
}
