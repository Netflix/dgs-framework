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

import com.github.benmanes.caffeine.cache.Cache
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.execution.preparsed.persisted.PersistedQueryCache
import java.util.function.Supplier

/**
 * Implementation of [PersistedQueryCache] backed by a Caffeine Cache.
 */
class AutomatedPersistedQueryCaffeineCache(val cache: Cache<String, PreparsedDocumentEntry>) :
    AutomatedPersistedQueryCacheAdapter() {

    override fun getFromCache(
        key: String,
        documentEntrySupplier: Supplier<PreparsedDocumentEntry>
    ): PreparsedDocumentEntry? {
        return cache.get(key) { documentEntrySupplier.get() }
    }
}
