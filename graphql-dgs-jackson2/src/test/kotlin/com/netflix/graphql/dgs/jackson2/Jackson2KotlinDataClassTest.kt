/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.graphql.dgs.jackson2

import com.netflix.graphql.dgs.internal.BaseDgsQueryExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Both mappers reachable once a consumer opts into Jackson 2 have to be usable from Kotlin.
 * A data class whose properties are all required deserializes even without
 * jackson-module-kotlin (ParameterNamesModule can drive a property-based creator), so the cases
 * that actually pin down the module's presence are the ones using a default value.
 */
class Jackson2KotlinDataClassTest {
    data class BookView(
        val title: String,
        val price: String,
    )

    /** A non-null property with a default value. */
    data class AuthorView(
        val name: String,
        val bio: String = "unknown",
    )

    private val bookJson = """{"title":"A Wizard of Earthsea","price":"USD 12.99"}"""
    private val authorMissingBio = """{"name":"Ursula K. Le Guin"}"""
    private val authorNullBio = """{"name":"Ursula K. Le Guin","bio":null}"""

    @Test
    fun `Jackson2DgsJsonMapper constructs a data class and applies defaults`() {
        val mapper = Jackson2DgsJsonMapper()

        assertThat(mapper.readValue(bookJson, BookView::class.java).title).isEqualTo("A Wizard of Earthsea")
        assertThat(mapper.readValue(authorMissingBio, AuthorView::class.java).bio).isEqualTo("unknown")
        // This mapper enables NullIsSameAsDefault, so an explicit null falls back to the default too.
        assertThat(mapper.readValue(authorNullBio, AuthorView::class.java).bio).isEqualTo("unknown")
    }

    @Test
    @Suppress("DEPRECATION")
    fun `deprecated BaseDgsQueryExecutor objectMapper constructs a data class and applies defaults`() {
        val mapper = BaseDgsQueryExecutor.getObjectMapper()

        assertThat(mapper.readValue(bookJson, BookView::class.java).title).isEqualTo("A Wizard of Earthsea")
        assertThat(mapper.readValue(authorMissingBio, AuthorView::class.java).bio).isEqualTo("unknown")
    }
}
