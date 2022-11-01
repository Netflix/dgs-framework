/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.DgsExecutionResult
import graphql.ExecutionResultImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

class DgsExecutionResultTest {
    @Test
    fun `should be able to pass null for data`() {
        assertThat(
            DgsExecutionResult
                .builder()
                .executionResult(ExecutionResultImpl.newExecutionResult().data(null))
                .build()
                .toSpecification()
        ).contains(entry("data", null))
    }

    @Test
    fun `should default to not having data`() {
        assertThat(
            DgsExecutionResult.builder().build().toSpecification()
        ).doesNotContainKey("data")
    }

    @Test
    fun `should be able to pass in custom data`() {
        val data = "Check under your chair"

        assertThat(
            DgsExecutionResult
                .builder()
                .executionResult(ExecutionResultImpl.newExecutionResult().data(data))
                .build()
                .toSpecification()
        ).contains(entry("data", data))
    }

    @Nested
    inner class ToSpringResponse {
        @Test
        fun `should create a spring response with specified headers`() {
            val headers = HttpHeaders()
            headers.add("We can add headers now??", "Yes we can")

            assertThat(
                DgsExecutionResult.builder().headers(headers).build().toSpringResponse()
                    .headers.toMap()
            ).containsAllEntriesOf(headers.toMap())
        }

        @Test
        fun `should create a spring response with specified http status code`() {
            val httpStatusCode = HttpStatus.ALREADY_REPORTED

            assertThat(
                DgsExecutionResult.builder().status(httpStatusCode).build().toSpringResponse()
                    .statusCode.value()
            ).isEqualTo(httpStatusCode.value())
        }
    }
}
