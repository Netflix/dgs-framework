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

package com.netflix.graphql.dgs.client

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL
import java.net.URLClassLoader

/**
 * The client's JSON mappers have to be usable from Kotlin, which means jackson-module-kotlin has
 * to be registered on them. Without it a data class whose properties are all required still
 * deserializes (Jackson 3 resolves parameter names natively, Jackson 2 has ParameterNamesModule),
 * so the regression only shows up on default values and explicit nulls - which is what these tests
 * pin down.
 *
 * jackson-module-kotlin is a compileOnly dependency, so it is registered conditionally. The last
 * test covers the other direction: a consumer without the module on its classpath must still be
 * able to build the mappers.
 */
class KotlinModuleSupportTest {
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
    fun `jackson3 adapter constructs a data class`() {
        val mapper = Jackson3DgsJsonMapperAdapter.defaultMapper()
        assertThat(mapper.readValue(bookJson, BookView::class.java).title).isEqualTo("A Wizard of Earthsea")
    }

    @Test
    fun `jackson3 adapter applies a default value for an absent property`() {
        val mapper = Jackson3DgsJsonMapperAdapter.defaultMapper()
        assertThat(mapper.readValue(authorMissingBio, AuthorView::class.java).bio).isEqualTo("unknown")
    }

    @Test
    fun `jackson3 adapter treats an explicit null as the default value`() {
        val mapper = Jackson3DgsJsonMapperAdapter.defaultMapper()
        assertThat(mapper.readValue(authorNullBio, AuthorView::class.java).bio).isEqualTo("unknown")
    }

    @Test
    fun `jackson2 adapter constructs a data class and applies defaults`() {
        val mapper = Jackson2DgsJsonMapperAdapter.defaultMapper()
        assertThat(mapper.readValue(bookJson, BookView::class.java).title).isEqualTo("A Wizard of Earthsea")
        assertThat(mapper.readValue(authorMissingBio, AuthorView::class.java).bio).isEqualTo("unknown")
        assertThat(mapper.readValue(authorNullBio, AuthorView::class.java).bio).isEqualTo("unknown")
    }

    @Test
    @Suppress("DEPRECATION")
    fun `deprecated GraphQLResponse constructs a data class and applies defaults`() {
        val response = GraphQLResponse("""{"data":{"book":$bookJson,"author":$authorMissingBio}}""")
        assertThat(response.extractValueAsObject("book", BookView::class.java).title)
            .isEqualTo("A Wizard of Earthsea")
        assertThat(response.extractValueAsObject("author", AuthorView::class.java).bio).isEqualTo("unknown")
    }

    @Test
    @Suppress("DEPRECATION")
    fun `deprecated createCustomObjectMapper constructs a data class and applies defaults`() {
        val mapper = GraphQLRequestOptions.createCustomObjectMapper()
        assertThat(mapper.readValue(bookJson, BookView::class.java).title).isEqualTo("A Wizard of Earthsea")
        assertThat(mapper.readValue(authorMissingBio, AuthorView::class.java).bio).isEqualTo("unknown")
        assertThat(mapper.readValue(authorNullBio, AuthorView::class.java).bio).isEqualTo("unknown")
    }

    /**
     * A consumer that does not bring jackson-module-kotlin - a plain Java service using
     * graphql-dgs-client - must still be able to build the mappers. Reloads the client classes in
     * a loader that hides the Kotlin module, so the conditional registration has to fall back.
     */
    @Test
    fun `mappers still build when jackson-module-kotlin is absent`() {
        withoutKotlinModule { loader ->
            val adapter = loader.loadClass("com.netflix.graphql.dgs.client.Jackson3DgsJsonMapperAdapter")
            val mapper = adapter.getMethod("defaultMapper").invoke(null)
            val readValue = adapter.getMethod("readValue", String::class.java, Class::class.java)

            // All properties present: works without the Kotlin module.
            val book = readValue.invoke(mapper, bookJson, BookView::class.java)
            assertThat(book).isEqualTo(BookView("A Wizard of Earthsea", "USD 12.99"))

            // Confirms the module really is hidden: without it the default value cannot be applied.
            assertThatThrownBy { readValue.invoke(mapper, authorMissingBio, AuthorView::class.java) }
                .rootCause()
                .hasMessageContaining("Parameter specified as non-null is null")
        }
    }

    private fun withoutKotlinModule(block: (ClassLoader) -> Unit) {
        val urls =
            System
                .getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { File(it).toURI().toURL() }
                .toTypedArray()
        HidingClassLoader(urls, javaClass.classLoader).use(block)
    }

    /**
     * Loads `com.netflix.graphql.dgs.client` classes itself rather than delegating, and refuses to
     * see the Jackson Kotlin modules at all.
     */
    private class HidingClassLoader(
        urls: Array<URL>,
        parent: ClassLoader,
    ) : URLClassLoader(urls, parent) {
        fun use(block: (ClassLoader) -> Unit) = use<HidingClassLoader, Unit> { block(it) }

        override fun loadClass(
            name: String,
            resolve: Boolean,
        ): Class<*> {
            if (HIDDEN.any { name.startsWith(it) }) {
                throw ClassNotFoundException("$name is hidden by this test")
            }
            if (OWNED.any { name.startsWith(it) }) {
                synchronized(getClassLoadingLock(name)) {
                    val existing = findLoadedClass(name)
                    if (existing != null) {
                        return existing
                    }
                    val loaded = findClass(name)
                    if (resolve) {
                        resolveClass(loaded)
                    }
                    return loaded
                }
            }
            return super.loadClass(name, resolve)
        }

        companion object {
            private val HIDDEN =
                listOf(
                    "tools.jackson.module.kotlin.",
                    "com.fasterxml.jackson.module.kotlin.",
                )
            private val OWNED = listOf("com.netflix.graphql.dgs.client.")
        }
    }
}
