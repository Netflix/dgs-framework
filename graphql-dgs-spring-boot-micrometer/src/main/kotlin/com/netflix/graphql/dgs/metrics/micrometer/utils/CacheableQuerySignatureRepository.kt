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

package com.netflix.graphql.dgs.metrics.micrometer.utils

import com.github.benmanes.caffeine.cache.Caffeine
import com.netflix.graphql.dgs.Internal
import com.netflix.graphql.dgs.metrics.micrometer.DgsGraphQLMetricsProperties
import com.netflix.graphql.dgs.metrics.micrometer.DgsMeterRegistrySupplier
import graphql.language.Document
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager
import java.util.*

/**
 * Implementation of a [QuerySignatureRepository] that will _cache_ the [QuerySignatureRepository.QuerySignature],
 * based on the calculated Query's _hash_ and _operation name_.
 *
 * This class will use by default a [Caffeine] cache, with a limit of [DEFAULT_MAX_CACHE_SIZE].
 * The cache will emit metrics according to [Micrometer's Cache Spec](https://micrometer.io/docs/ref/cache).
 * The name of the cache is `dgsQuerySignatureCache`, as defined by [QUERY_SIG_CACHE].
 *
 * You can override the internal cache if a [CacheManager] is provided with a pre-configured named cache matching the
 * [QUERY_SIG_CACHE] name. In Spring Boot you can preconfigure a cache via the following properties:
 *
 * ```
 * "spring.cache.cache-names=dgsQuerySignatureCache"
 * ```
 *
 * And for example, set a new limit via
 * ```
 * "spring.cache.caffeine.spec=maximumSize=500"
 * ```
 *
 * For additional information consult [Spring Boot's Cache Guide](https://docs.spring.io/spring-boot/docs/2.3.x/reference/htmlsingle/#boot-features-caching).
 */
@Internal
open class CacheableQuerySignatureRepository(
    properties: DgsGraphQLMetricsProperties,
    meterRegistrySupplier: DgsMeterRegistrySupplier,
    private val optionalCacheManager: Optional<CacheManager>,
) : SimpleQuerySignatureRepository(properties, meterRegistrySupplier) {

    private lateinit var cache: Cache

    companion object {
        private val log: Logger = LoggerFactory.getLogger(CacheableQuerySignatureRepository::class.java)
        const val DEFAULT_MAX_CACHE_SIZE = 100L
        const val QUERY_SIG_CACHE = "dgsQuerySignatureCache"
    }

    override fun computeQuerySignature(
        queryHash: String,
        queryName: String?,
        document: Document
    ): QuerySignatureRepository.QuerySignature {
        val key = CacheKey(queryHash, queryName)
        log.debug("Computing query signature for query with cache key: {}.", key)
        return cache.get(key) { super.computeQuerySignature(queryHash, queryName, document) }!!
    }

    override fun afterPropertiesSet() {
        super.afterPropertiesSet()
        cache = Objects.requireNonNull(
            optionalCacheManager
                .filter { it !== null && it.cacheNames.contains(QUERY_SIG_CACHE) }
                .flatMap { Optional.ofNullable(it.getCache(QUERY_SIG_CACHE)) }
                .orElseGet { newMonitoredCaffeineCacheManager().getCache(QUERY_SIG_CACHE) },
            "Expected to resolve named cache[$QUERY_SIG_CACHE] from either the internal cache manager or the optional!"
        )
    }

    private fun newMonitoredCaffeineCacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager()
        val customCaffeineCache =
            Caffeine
                .newBuilder()
                .maximumSize(DEFAULT_MAX_CACHE_SIZE)
                .recordStats()
                .build<Any, Any>()

        val meteredCache =
            CaffeineCacheMetrics.monitor(meterRegistry, customCaffeineCache, QUERY_SIG_CACHE)
        cacheManager.registerCustomCache(QUERY_SIG_CACHE, meteredCache)
        return cacheManager
    }

    internal fun fetchRawValueFromCache(key: CacheKey): Optional<QuerySignatureRepository.QuerySignature> {
        return Optional.ofNullable(cache.get(key)).map { it.get() as QuerySignatureRepository.QuerySignature }
    }

    internal data class CacheKey(val hash: String, val name: String?)
}
