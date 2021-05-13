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

import com.netflix.graphql.dgs.client.codegen.InputValueSerializer
import com.netflix.graphql.dgs.client.scalar.DateRange
import com.netflix.graphql.dgs.client.scalar.DateRangeScalar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class InputValueSerializerTest {

    @Test
    fun `Serialize a complex object`() {

        val movieInput = MovieInput(
            1,
            "Some movie",
            MovieInput.Genre.ACTION,
            MovieInput.Director("The Director"),
            listOf(
                MovieInput.Actor("Actor 1", "Role 1"),
                MovieInput.Actor("Actor 2", "Role 2"),
            ),
            DateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1))
        )

        val serialize = InputValueSerializer(mapOf(DateRange::class.java to DateRangeScalar())).serialize(movieInput)
        assertThat(serialize).isEqualTo("{movieId:1, title:\"Some movie\", genre:ACTION, director:{name:\"The Director\" }, actor:[{name:\"Actor 1\", roleName:\"Role 1\" }, {name:\"Actor 2\", roleName:\"Role 2\" }], releaseWindow:\"01/01/2020-01/01/2021\" }")
    }

    @Test
    fun `Null values should be skipped`() {

        val movieInput = MovieInput(1)

        val serialize = InputValueSerializer(mapOf(DateRange::class.java to DateRangeScalar())).serialize(movieInput)
        assertThat(serialize).isEqualTo("{movieId:1 }")
    }

    @Test
    fun `String value`() {
        val serialize = InputValueSerializer().serialize("some string")
        assertThat(serialize).isEqualTo("\"some string\"")
    }

    @Test
    fun `int value`() {
        val serialize = InputValueSerializer().serialize(1)
        assertThat(serialize).isEqualTo("1")
    }

    @Test
    fun `long value`() {
        val serialize = InputValueSerializer().serialize(1L)
        assertThat(serialize).isEqualTo("1")
    }

    @Test
    fun `boolean value`() {
        val serialize = InputValueSerializer().serialize(true)
        assertThat(serialize).isEqualTo("true")
    }

    @Test
    fun `double value`() {
        val serialize = InputValueSerializer().serialize(1.1)
        assertThat(serialize).isEqualTo("1.1")
    }

    @Test
    fun `Companion objects should be ignored`() {
        val serialize = InputValueSerializer().serialize(MyDataWithCompanion("some title"))
        assertThat(serialize).isEqualTo("{title:\"some title\" }")
    }

    data class MyDataWithCompanion(val title: String) {
        public companion object {}
    }
}
