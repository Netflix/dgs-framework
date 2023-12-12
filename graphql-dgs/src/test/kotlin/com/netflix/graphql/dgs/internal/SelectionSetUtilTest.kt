/*
 * Copyright 2023 Netflix, Inc.
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

import com.netflix.graphql.dgs.internal.utils.SelectionSetUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SelectionSetUtilTest {
    @Test
    fun `toPaths handles nested structures`() {
        val paths = SelectionSetUtil.toPaths(
            """
            outer {
              fieldA
              fieldB
            }
            fieldC
            """.trimIndent()
        )

        assertThat(paths).isEqualTo(
            listOf(
                listOf("outer", "fieldA"),
                listOf("outer", "fieldB"),
                listOf("fieldC")
            )
        )
    }

    @Test
    fun `ignores spread selections`() {
        val paths = SelectionSetUtil.toPaths(
            """
            outer {
              fieldA
              fieldB
              ... on SomeType {
                hopefullyIgnored
              }
              ... on SomeOtherType {
                alsoIgnored {
                  nested
                }
              }
            }
            fieldC
            """.trimIndent()
        )

        assertThat(paths).isEqualTo(
            listOf(
                listOf("outer", "fieldA"),
                listOf("outer", "fieldB"),
                listOf("fieldC")
            )
        )
    }
}
