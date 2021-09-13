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

package com.netflix.graphql.dgs.autoconfig

import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.autoconfig.testcomponents.TestExceptionDatFetcherConfig
import com.netflix.graphql.dgs.exceptions.QueryException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner

class DefaultExceptionHandlerTest {
    private val context = WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(DgsAutoConfiguration::class.java))!!

    @Test
    fun queryDocumentWithDefaultException() {
        val error: QueryException = assertThrows {
            context.withUserConfiguration(TestExceptionDatFetcherConfig::class.java).run { ctx ->
                assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                    it.executeAndGetDocumentContext("{errorTest}")
                }
            }
        }
        assertThat(error.errors.size).isEqualTo(1)

        assertThat(error.errors[0].extensions["errorType"]).isEqualTo("INTERNAL")
    }
}
