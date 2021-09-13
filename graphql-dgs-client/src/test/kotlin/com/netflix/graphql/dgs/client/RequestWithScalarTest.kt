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

package com.netflix.graphql.dgs.client

import com.netflix.graphql.dgs.client.scalar.DateRange
import com.netflix.graphql.dgs.client.scalar.DateRangeScalar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RequestWithScalarTest {

    @Test
    fun `Creating a request with a date-time scalar as a variable should serialize correctly`() {

        val variables = mapOf(
            "currentDateTime" to DateRangeScalar().serialize(
                DateRange(
                    from = LocalDate.now(),
                    to = LocalDate.now().plusDays(1)
                )
            )
        )
        val graphQLResponse = DefaultGraphQLClient("").executeQuery(
            """
                   query Calendar(${'$'}timePeriod: DateRange) {
                     getMeetings(timePeriod: ${'$'}timePeriod)
                   }
            """.trimIndent(),
            variables, this::mockRequestHandler
        )

        assertThat(graphQLResponse).isNotNull
    }

    private fun mockRequestHandler(url: String, headers: Map<String, List<String>>, body: String): HttpResponse =
        HttpResponse(
            200,
            """
            {
                "data": null
            }
            """.trimIndent()
        )
}
