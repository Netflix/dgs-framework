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

package com.netflix.graphql.dgs.metrics.micrometer.utils

import com.netflix.graphql.dgs.metrics.micrometer.DgsGraphQLMicrometerAutoConfiguration
import com.netflix.graphql.dgs.metrics.micrometer.utils.SimpleQuerySignatureRepositoryTest.Companion.EXPECTED_FOO_SIG_HASH
import com.netflix.graphql.dgs.metrics.micrometer.utils.SimpleQuerySignatureRepositoryTest.Companion.QUERY
import com.netflix.graphql.dgs.metrics.micrometer.utils.SimpleQuerySignatureRepositoryTest.Companion.expectedFooDoc
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import java.util.Optional

internal class CacheableQuerySignatureRepositoryTest {
    @Nested
    @SpringBootTest(
        classes = [
            DgsGraphQLMicrometerAutoConfiguration.MetricsPropertiesConfiguration::class,
            DgsGraphQLMicrometerAutoConfiguration.MeterRegistryConfiguration::class,
            DgsGraphQLMicrometerAutoConfiguration.QuerySignatureRepositoryConfiguration::class,
        ],
    )
    inner class NoCacheManagerAvailable : BaseCacheableQuerySignatureRepositoryTest() {
        override fun assertCacheManager(
            expectedKey: CacheableQuerySignatureRepository.CacheKey,
            expectedValue: QuerySignatureRepository.QuerySignature,
            optCacheManager: Optional<CacheManager>,
        ) {
            assertThat(optCacheManager).isEmpty
        }
    }

    @Nested
    @SpringBootTest(
        properties = [
            "spring.cache.cache-names=dgsQuerySignatureCache",
            "spring.cache.caffeine.spec=maximumSize=500",
        ],
        classes = [
            CacheAutoConfiguration::class,
            DgsGraphQLMicrometerAutoConfiguration.MetricsPropertiesConfiguration::class,
            DgsGraphQLMicrometerAutoConfiguration.MeterRegistryConfiguration::class,
            DgsGraphQLMicrometerAutoConfiguration.QuerySignatureRepositoryConfiguration::class,
        ],
    )
    @EnableCaching
    inner class CacheManagerAvailableWithQuerySigCache : BaseCacheableQuerySignatureRepositoryTest() {
        override fun assertCacheManager(
            expectedKey: CacheableQuerySignatureRepository.CacheKey,
            expectedValue: QuerySignatureRepository.QuerySignature,
            optCacheManager: Optional<CacheManager>,
        ) {
            assertThat(optCacheManager)
                .get()
                .extracting {
                    it.getCache(CacheableQuerySignatureRepository.QUERY_SIG_CACHE)?.get(expectedKey)?.get()
                }.isEqualTo(expectedValue)
        }
    }

    @Nested
    @SpringBootTest(
        classes = [
            CacheAutoConfiguration::class,
            DgsGraphQLMicrometerAutoConfiguration.MetricsPropertiesConfiguration::class,
            DgsGraphQLMicrometerAutoConfiguration.MeterRegistryConfiguration::class,
            DgsGraphQLMicrometerAutoConfiguration.QuerySignatureRepositoryConfiguration::class,
        ],
    )
    @EnableCaching
    inner class CacheManagerAvailableWithNoQuerySigCache : BaseCacheableQuerySignatureRepositoryTest() {
        override fun assertCacheManager(
            expectedKey: CacheableQuerySignatureRepository.CacheKey,
            expectedValue: QuerySignatureRepository.QuerySignature,
            optCacheManager: Optional<CacheManager>,
        ) {
            assertThat(optCacheManager)
                .get()
                .extracting {
                    it.getCache(CacheableQuerySignatureRepository.QUERY_SIG_CACHE)?.get(expectedKey)
                }.isNull()
        }
    }

    internal abstract class BaseCacheableQuerySignatureRepositoryTest {
        @Autowired
        lateinit var repository: CacheableQuerySignatureRepository

        @Autowired
        lateinit var optCacheManager: Optional<CacheManager>

        @Test
        fun `Is able to cache a query signature`() {
            val document = SimpleQuerySignatureRepositoryTest.parseQuery(QUERY)
            val parameters: InstrumentationExecutionParameters = mock(InstrumentationExecutionParameters::class.java)

            val queryHash = QuerySignatureRepository.queryHash(QUERY)
            val queryName = "Foo"

            Mockito.`when`(parameters.query).thenReturn(QUERY)
            Mockito.`when`(parameters.operation).thenReturn(queryName)

            val optQuerySignature = repository.get(document, parameters)

            assertThat(optQuerySignature).isNotEmpty
            val sig = optQuerySignature.get()
            assertThat(sig.value).isEqualToNormalizingWhitespace(expectedFooDoc)
            assertThat(sig.hash).isEqualTo(EXPECTED_FOO_SIG_HASH)

            assertThat(repository.get(document, parameters)).isEqualTo(optQuerySignature)

            val expectedCacheKey = CacheableQuerySignatureRepository.CacheKey(queryHash, queryName)
            val cachedValue = repository.fetchRawValueFromCache(expectedCacheKey)
            assertThat(cachedValue).get().isEqualTo(sig)

            assertCacheManager(expectedCacheKey, sig, optCacheManager)
        }

        abstract fun assertCacheManager(
            expectedKey: CacheableQuerySignatureRepository.CacheKey,
            expectedValue: QuerySignatureRepository.QuerySignature,
            optCacheManager: Optional<CacheManager>,
        )
    }
}
