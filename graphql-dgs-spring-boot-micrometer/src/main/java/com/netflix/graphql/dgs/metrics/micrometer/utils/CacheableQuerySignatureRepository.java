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

package com.netflix.graphql.dgs.metrics.micrometer.utils;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.netflix.graphql.dgs.Internal;
import com.netflix.graphql.dgs.metrics.micrometer.DgsMeterRegistrySupplier;
import graphql.language.Document;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.data.metrics.AutoTimer;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of a {@link QuerySignatureRepository} that will <em>cache</em> the
 * {@link QuerySignatureRepository.QuerySignature}, based on the calculated Query's <em>hash</em> and
 * <em>operation name</em>.
 *
 * <p>This class will use by default a {@link Caffeine} cache, with a limit of {@link #DEFAULT_MAX_CACHE_SIZE}.
 * The cache will emit metrics according to <a href="https://micrometer.io/docs/ref/cache">Micrometer's Cache Spec</a>.
 * The name of the cache is {@code dgsQuerySignatureCache}, as defined by {@link #QUERY_SIG_CACHE}.
 *
 * <p>You can override the internal cache if a {@link CacheManager} is provided with a pre-configured named cache
 * matching the {@link #QUERY_SIG_CACHE} name. In Spring Boot you can preconfigure a cache via the following
 * properties:
 *
 * <pre>{@code spring.cache.cache-names=dgsQuerySignatureCache}</pre>
 *
 * <p>And for example, set a new limit via
 *
 * <pre>{@code spring.cache.caffeine.spec=maximumSize=500}</pre>
 */
@Internal
public class CacheableQuerySignatureRepository extends SimpleQuerySignatureRepository {
    private static final Logger log = LoggerFactory.getLogger(CacheableQuerySignatureRepository.class);

    public static final long DEFAULT_MAX_CACHE_SIZE = 100L;
    public static final String QUERY_SIG_CACHE = "dgsQuerySignatureCache";

    private final Optional<CacheManager> optionalCacheManager;

    private Cache cache;

    public CacheableQuerySignatureRepository(
            AutoTimer autoTimer,
            DgsMeterRegistrySupplier meterRegistrySupplier,
            Optional<CacheManager> optionalCacheManager) {
        super(autoTimer, meterRegistrySupplier);
        this.optionalCacheManager = optionalCacheManager;
    }

    @Override
    protected QuerySignature computeQuerySignature(String queryHash, String queryName, Document document) {
        CacheKey key = new CacheKey(queryHash, queryName);
        log.debug("Computing query signature for query with cache key: {}.", key);
        return cache.get(key, () -> super.computeQuerySignature(queryHash, queryName, document));
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        cache =
                Objects.requireNonNull(
                        optionalCacheManager
                                .filter(cacheManager -> cacheManager.getCacheNames().contains(QUERY_SIG_CACHE))
                                .flatMap(cacheManager -> Optional.ofNullable(cacheManager.getCache(QUERY_SIG_CACHE)))
                                .orElseGet(() -> newMonitoredCaffeineCacheManager().getCache(QUERY_SIG_CACHE)),
                        "Expected to resolve named cache[" + QUERY_SIG_CACHE
                                + "] from either the internal cache manager or the optional!");
    }

    private CacheManager newMonitoredCaffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        com.github.benmanes.caffeine.cache.Cache<Object, Object> customCaffeineCache =
                Caffeine
                        .newBuilder()
                        .maximumSize(DEFAULT_MAX_CACHE_SIZE)
                        .recordStats()
                        .build();

        var meteredCache = CaffeineCacheMetrics.monitor(meterRegistry, customCaffeineCache, QUERY_SIG_CACHE);
        cacheManager.registerCustomCache(QUERY_SIG_CACHE, meteredCache);
        return cacheManager;
    }

    Optional<QuerySignature> fetchRawValueFromCache(CacheKey key) {
        return Optional.ofNullable(cache.get(key)).map(wrapper -> (QuerySignature) wrapper.get());
    }

    public static final class CacheKey {
        private final String hash;
        private final String name;

        public CacheKey(String hash, String name) {
            this.hash = hash;
            this.name = name;
        }

        public String getHash() {
            return hash;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof CacheKey that
                    && Objects.equals(hash, that.hash)
                    && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hash, name);
        }

        @Override
        public String toString() {
            return "CacheKey(hash=" + hash + ", name=" + name + ")";
        }
    }
}
