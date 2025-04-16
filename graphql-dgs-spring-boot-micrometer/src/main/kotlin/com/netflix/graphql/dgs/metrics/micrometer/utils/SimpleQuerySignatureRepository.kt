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

import com.netflix.graphql.dgs.Internal
import com.netflix.graphql.dgs.metrics.DgsMetrics.CommonTags
import com.netflix.graphql.dgs.metrics.DgsMetrics.InternalMetric
import com.netflix.graphql.dgs.metrics.micrometer.DgsMeterRegistrySupplier
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.language.Document
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.actuate.metrics.AutoTimer
import java.util.Optional

/**
 * Basic implementation of a [QuerySignatureRepository]. The time it takes to calculate the query signature will be
 * exposed by the [InternalMetric.TIMED_METHOD] key having the class and method name as tags along if the execution
 * was a success or not, ref [CommonTags.SUCCESS] or [CommonTags.FAILURE].
 */
@Internal
open class SimpleQuerySignatureRepository(
    private val autoTimer: AutoTimer,
    private val meterRegistrySupplier: DgsMeterRegistrySupplier,
) : QuerySignatureRepository,
    InitializingBean {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(SimpleQuerySignatureRepository::class.java)
    }

    protected lateinit var meterRegistry: MeterRegistry

    override fun get(
        document: Document,
        parameters: InstrumentationExecutionParameters,
    ): Optional<QuerySignatureRepository.QuerySignature> {
        val timerSample = Timer.start(meterRegistry)
        val tags = mutableListOf<Tag>()
        val queryHash = QuerySignatureRepository.queryHash(parameters.query)
        return try {
            val result =
                Optional.ofNullable(
                    computeQuerySignature(
                        queryHash,
                        parameters.operation,
                        document,
                    ),
                )
            tags += CommonTags.SUCCESS.tag
            return result
        } catch (error: Throwable) {
            tags += CommonTags.FAILURE.tags(error)
            log.error(
                "Failed to fetch query signature from cache, query [hash:{}, name:{}].",
                queryHash,
                parameters.operation,
            )
            Optional.empty()
        } finally {
            tags += CommonTags.JAVA_CLASS.tags(this)
            tags += CommonTags.JAVA_CLASS_METHOD.tags("get")
            timerSample.stop(
                autoTimer
                    .builder(InternalMetric.TIMED_METHOD.key)
                    .tags(tags)
                    .register(meterRegistry),
            )
        }
    }

    protected open fun computeQuerySignature(
        queryHash: String,
        queryName: String?,
        document: Document,
    ): QuerySignatureRepository.QuerySignature = QuerySignatureRepository.computeSignature(document, queryName)

    override fun afterPropertiesSet() {
        this.meterRegistry = meterRegistrySupplier.get()
    }
}
