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

package com.netflix.graphql.dgs.metrics.micrometer.dataloader

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class BatchLoaderWithContextInterceptorTest {
    @Test
    fun `batch size 0 is bucketed to 5`() {
        assertThat(BatchLoaderWithContextInterceptor.bucketBatchSize(0)).isEqualTo(5)
    }

    @Test
    fun `batch size 1 is bucketed to 5`() {
        assertThat(BatchLoaderWithContextInterceptor.bucketBatchSize(1)).isEqualTo(5)
    }

    @Test
    fun `batch size 4 is bucketed to 5`() {
        assertThat(BatchLoaderWithContextInterceptor.bucketBatchSize(4)).isEqualTo(5)
    }

    @Test
    fun `batch size 5 is bucketed to 10`() {
        assertThat(BatchLoaderWithContextInterceptor.bucketBatchSize(5)).isEqualTo(10)
    }

    @Test
    fun `batch size 9 is bucketed to 10`() {
        assertThat(BatchLoaderWithContextInterceptor.bucketBatchSize(9)).isEqualTo(10)
    }

    @Test
    fun `batch size 10 is bucketed to 25`() {
        assertThat(BatchLoaderWithContextInterceptor.bucketBatchSize(10)).isEqualTo(25)
    }

    @Test
    fun `batch size 24 is bucketed to 25`() {
        assertThat(BatchLoaderWithContextInterceptor.bucketBatchSize(24)).isEqualTo(25)
    }

    @Test
    fun `batch size 25 is bucketed to 50`() {
        assertThat(BatchLoaderWithContextInterceptor.bucketBatchSize(25)).isEqualTo(50)
    }

    @Test
    fun `batch size 99 is bucketed to 100`() {
        assertThat(BatchLoaderWithContextInterceptor.bucketBatchSize(99)).isEqualTo(100)
    }

    @Test
    fun `batch size 100 is bucketed to 200`() {
        assertThat(BatchLoaderWithContextInterceptor.bucketBatchSize(100)).isEqualTo(200)
    }

    @Test
    fun `batch size 500 is bucketed to 1000`() {
        assertThat(BatchLoaderWithContextInterceptor.bucketBatchSize(500)).isEqualTo(1000)
    }

    @Test
    fun `batch size 9999 is bucketed to 10000`() {
        assertThat(BatchLoaderWithContextInterceptor.bucketBatchSize(9999)).isEqualTo(10000)
    }

    @Test
    fun `batch size 10000 exceeds all buckets`() {
        assertThat(BatchLoaderWithContextInterceptor.bucketBatchSize(10000)).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `batch size 50000 exceeds all buckets`() {
        assertThat(BatchLoaderWithContextInterceptor.bucketBatchSize(50000)).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `all bucket boundaries produce at most 12 distinct values`() {
        val distinctValues =
            (0..50000).map { BatchLoaderWithContextInterceptor.bucketBatchSize(it) }.distinct().sorted()
        assertThat(distinctValues).hasSize(12)
        assertThat(distinctValues).containsExactly(5, 10, 25, 50, 100, 200, 500, 1000, 2000, 5000, 10000, Int.MAX_VALUE)
    }
}
