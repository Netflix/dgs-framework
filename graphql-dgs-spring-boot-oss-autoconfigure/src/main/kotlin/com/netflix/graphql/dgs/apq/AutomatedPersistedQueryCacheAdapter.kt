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

package com.netflix.graphql.dgs.apq

import graphql.ExecutionInput
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.execution.preparsed.persisted.PersistedQueryCache
import graphql.execution.preparsed.persisted.PersistedQueryCacheMiss
import graphql.execution.preparsed.persisted.PersistedQueryNotFound
import graphql.execution.preparsed.persisted.PersistedQuerySupport
import org.apache.commons.lang3.StringUtils
import java.util.function.Supplier

/**
 * Adapter that is intended to facilitate the implementation of a [PersistedQueryCache] that can be used to
 * store _Automated Persisted Queries_. Refer to [AutomatedPersistedQueryCaffeineCache] for an example.
 *
 * @see DgsAPQSupportAutoConfiguration
 */
abstract class AutomatedPersistedQueryCacheAdapter : PersistedQueryCache {

    override fun getPersistedQueryDocument(
        persistedQueryId: Any,
        executionInput: ExecutionInput,
        onCacheMiss: PersistedQueryCacheMiss
    ): PreparsedDocumentEntry? {
        val key = when (persistedQueryId) {
            is String -> persistedQueryId
            else -> persistedQueryId.toString()
        }
        return getFromCache(key) {
            // Get the query from the execution input. Make sure it's not null, empty or the APQ marker.
            val queryText = executionInput.query
            if (StringUtils.isBlank(queryText) || queryText.equals(PersistedQuerySupport.PERSISTED_QUERY_MARKER)) {
                throw PersistedQueryNotFound(persistedQueryId)
            }
            return@getFromCache onCacheMiss.apply(queryText)
        }
    }

    /**
     * Obtains the [PreparsedDocumentEntry] associated with the [key] from the cache that backs the implementation.
     * If the document is missing from the [documentEntrySupplier] will provide one, it should be added to the cache
     * then.
     *
     * @param key The hash of the requested query.
     * @param documentEntrySupplier function that will supply the document in case there is a cache miss.
     */
    protected abstract fun getFromCache(
        key: String,
        documentEntrySupplier: Supplier<PreparsedDocumentEntry>
    ): PreparsedDocumentEntry?
}
