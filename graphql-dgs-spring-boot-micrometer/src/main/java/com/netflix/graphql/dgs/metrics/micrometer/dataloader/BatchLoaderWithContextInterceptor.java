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

package com.netflix.graphql.dgs.metrics.micrometer.dataloader;

import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlMetric;
import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

class BatchLoaderWithContextInterceptor implements InvocationHandler {
    private static final String ID = GqlMetric.DATA_LOADER.getKey();
    private static final Logger logger = LoggerFactory.getLogger(BatchLoaderWithContextInterceptor.class);

    private final Object batchLoaderWithContext;
    private final String name;
    private final MeterRegistry registry;

    BatchLoaderWithContextInterceptor(Object batchLoaderWithContext, String name, MeterRegistry registry) {
        this.batchLoaderWithContext = batchLoaderWithContext;
        this.name = name;
        this.registry = registry;
    }

    @Override
    public CompletionStage<?> invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("load".equals(method.getName())) {
            logger.debug("Starting metered timer[{}] for {}.", ID, getClass().getSimpleName());
            Timer.Sample timerSampler = Timer.start(registry);
            try {
                CompletionStage<?> future = (CompletionStage<?>) method.invoke(batchLoaderWithContext, args);
                return future.whenComplete((result, ignored) -> {
                    logger.debug("Stopping timer[{}] for {}", ID, getClass().getSimpleName());

                    int resultSize;
                    if (result instanceof List<?> list) {
                        resultSize = list.size();
                    } else if (result instanceof Map<?, ?> map) {
                        resultSize = map.size();
                    } else {
                        throw new IllegalStateException(
                                "BatchLoader or MappedBatchLoader should always return a List/Map. A "
                                        + result.getClass().getName() + " was found.");
                    }

                    timerSampler.stop(
                            Timer
                                    .builder(ID)
                                    .tags(List.of(
                                            Tag.of(GqlTag.LOADER_NAME.getKey(), name),
                                            Tag.of(GqlTag.LOADER_BATCH_SIZE.getKey(), String.valueOf(resultSize))))
                                    .register(registry));
                });
            } catch (InvocationTargetException exception) {
                throw exception.getTargetException();
            } catch (Exception exception) {
                logger.warn(
                        "Error creating timer interceptor '{}' for {} with exception {}",
                        ID,
                        getClass().getSimpleName(),
                        exception.getMessage());
                return (CompletionStage<?>) method.invoke(batchLoaderWithContext, args);
            }
        }
        throw new UnsupportedOperationException("Unsupported method: " + method.getName());
    }
}
