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

package com.netflix.graphql.dgs.springgraphql.apq

import com.netflix.graphql.dgs.apq.DgsAPQPreParsedDocumentProviderWrapper
import graphql.ExecutionInput
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.execution.preparsed.PreparsedDocumentProvider
import graphql.execution.preparsed.persisted.PersistedQueryCache
import graphql.execution.preparsed.persisted.PersistedQuerySupport.PERSISTED_QUERY_MARKER
import graphql.language.Document
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import java.util.concurrent.CompletableFuture

@ExtendWith(MockKExtension::class)
class DgsAPQPreParsedDocumentProviderWrapperTest {
    @Autowired
    private lateinit var dgsAPQPreParsedDocumentProvider: DgsAPQPreParsedDocumentProviderWrapper

    @MockK
    lateinit var preparsedDocumentProvider: PreparsedDocumentProvider

    @MockK
    lateinit var persistedQueryCache: PersistedQueryCache

    @BeforeEach
    fun setUp() {
        dgsAPQPreParsedDocumentProvider =
            DgsAPQPreParsedDocumentProviderWrapper(persistedQueryCache, Optional.of(preparsedDocumentProvider))
    }

    @Test
    fun `APQ only queries with just the hash use the persisted query cache`() {
        var count = 0
        val document = mockk<Document>()
        val computeFunction = { _: ExecutionInput ->
            count++
            PreparsedDocumentEntry(document)
        }
        val extensions: MutableMap<String, Any> = HashMap()
        extensions["persistedQuery"] =
            mapOf("version" to "1", "sha256Hash" to "ecf4edb46db40b5132295c0291d62fb65d6759a9eedfa4d5d612dd5ec54a6b38")
        val executionInput =
            ExecutionInput
                .Builder()
                .query(PERSISTED_QUERY_MARKER)
                .extensions(extensions)
                .build()

        every {
            persistedQueryCache.getPersistedQueryDocumentAsync(any(), any(), any())
        }.returns(CompletableFuture.completedFuture(computeFunction(executionInput)))

        dgsAPQPreParsedDocumentProvider.getDocumentAsync(executionInput, computeFunction)
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `APQ queries with query and hash use the persisted query cache`() {
        var count = 0
        val document = mockk<Document>()
        val computeFunction = { _: ExecutionInput ->
            count++
            PreparsedDocumentEntry(document)
        }
        val extensions: MutableMap<String, Any> = HashMap()
        extensions["persistedQuery"] =
            mapOf("version" to "1", "sha256Hash" to "ecf4edb46db40b5132295c0291d62fb65d6759a9eedfa4d5d612dd5ec54a6b38")
        val executionInput =
            ExecutionInput
                .Builder()
                .query("{__typename}")
                .extensions(extensions)
                .build()

        every {
            persistedQueryCache.getPersistedQueryDocumentAsync(any(), any(), any())
        }.returns(CompletableFuture.completedFuture(computeFunction(executionInput)))

        dgsAPQPreParsedDocumentProvider.getDocumentAsync(executionInput, computeFunction)
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `Plain queries (non-APQ) use the user specified preparsed document provider`() {
        var count = 0
        val document = mockk<Document>()
        val computeFunction = { _: ExecutionInput ->
            count++
            PreparsedDocumentEntry(document)
        }
        val extensions: MutableMap<String, Any> = HashMap()
        extensions["persistedQuery"] =
            mapOf("version" to "1", "sha256Hash" to "ecf4edb46db40b5132295c0291d62fb65d6759a9eedfa4d5d612dd5ec54a6b38")
        val executionInput = ExecutionInput.Builder().query("{__typename}").build()

        every {
            preparsedDocumentProvider.getDocumentAsync(executionInput, any())
        }.returns(CompletableFuture.completedFuture(computeFunction(executionInput)))

        dgsAPQPreParsedDocumentProvider.getDocumentAsync(executionInput, computeFunction)
        assertThat(count).isEqualTo(1)
    }
}
