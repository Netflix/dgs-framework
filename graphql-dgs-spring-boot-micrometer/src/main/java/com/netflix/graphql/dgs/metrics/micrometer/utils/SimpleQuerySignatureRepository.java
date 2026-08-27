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

import com.netflix.graphql.dgs.Internal;
import com.netflix.graphql.dgs.metrics.DgsMetrics.CommonTags;
import com.netflix.graphql.dgs.metrics.DgsMetrics.InternalMetric;
import com.netflix.graphql.dgs.metrics.micrometer.DgsMeterRegistrySupplier;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.language.Document;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.data.metrics.AutoTimer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Basic implementation of a {@link QuerySignatureRepository}. The time it takes to calculate the query signature will
 * be exposed by the {@link InternalMetric#TIMED_METHOD} key having the class and method name as tags along if the
 * execution was a success or not, ref {@link CommonTags#SUCCESS} or {@link CommonTags#FAILURE}.
 */
@Internal
public class SimpleQuerySignatureRepository implements QuerySignatureRepository, InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(SimpleQuerySignatureRepository.class);

    private final AutoTimer autoTimer;
    private final DgsMeterRegistrySupplier meterRegistrySupplier;

    protected MeterRegistry meterRegistry;

    public SimpleQuerySignatureRepository(AutoTimer autoTimer, DgsMeterRegistrySupplier meterRegistrySupplier) {
        this.autoTimer = autoTimer;
        this.meterRegistrySupplier = meterRegistrySupplier;
    }

    @Override
    public Optional<QuerySignature> get(Document document, InstrumentationExecutionParameters parameters) {
        Timer.Sample timerSample = Timer.start(meterRegistry);
        List<Tag> tags = new ArrayList<>();
        String queryHash = QuerySignatureRepository.queryHash(parameters.getQuery());
        try {
            Optional<QuerySignature> result =
                    Optional.ofNullable(computeQuerySignature(queryHash, parameters.getOperation(), document));
            tags.add(CommonTags.SUCCESS.getTag());
            return result;
        } catch (Throwable error) {
            CommonTags.FAILURE.tags(error).forEach(tags::add);
            log.error(
                    "Failed to fetch query signature from cache, query [hash:{}, name:{}].",
                    queryHash,
                    parameters.getOperation());
            return Optional.empty();
        } finally {
            CommonTags.JAVA_CLASS.tags(this).forEach(tags::add);
            CommonTags.JAVA_CLASS_METHOD.tags("get").forEach(tags::add);
            timerSample.stop(
                    autoTimer
                            .builder(InternalMetric.TIMED_METHOD.getKey())
                            .tags(tags)
                            .register(meterRegistry));
        }
    }

    protected QuerySignature computeQuerySignature(String queryHash, String queryName, Document document) {
        return QuerySignatureRepository.computeSignature(document, queryName);
    }

    @Override
    public void afterPropertiesSet() {
        this.meterRegistry = meterRegistrySupplier.get();
    }
}
