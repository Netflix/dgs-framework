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

package com.netflix.graphql.dgs.exceptions

import graphql.execution.ResultPath
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DgsExceptionTest {
    companion object {
        const val FAKE_ERROR_MESSAGE = "dgs message"
    }

    @Nested
    inner class ToGraphQlError {
        @Test
        fun `should default to not include path`() {
            assertThat(
                CustomDgsException()
                    .toGraphQlError() // no path
                    .path,
            ).isNull()
        }

        @Test
        fun `should include path if explicitly asked`() {
            val pathSegments = listOf("path", "pathObj")

            assertThat(
                CustomDgsException()
                    .toGraphQlError(path = ResultPath.fromList(pathSegments))
                    .path,
            ).containsAll(pathSegments)
        }

        @Test
        fun `should contain error class name in extensions`() {
            assertThat(
                CustomDgsException()
                    .toGraphQlError()
                    .extensions,
            ).contains(
                entry(
                    DgsException.EXTENSION_CLASS_KEY,
                    CustomDgsException::class.java.name,
                ),
            )
        }
    }

    private inner class CustomDgsException :
        DgsException(
            message = FAKE_ERROR_MESSAGE,
        )
}
