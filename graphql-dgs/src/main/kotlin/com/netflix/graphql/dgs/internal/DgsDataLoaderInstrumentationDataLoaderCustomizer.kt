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

package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.DgsDataLoaderCustomizer
import com.netflix.graphql.dgs.DgsDataLoaderInstrumentation
import com.netflix.graphql.dgs.DgsDataLoaderRegistryConsumer
import com.netflix.graphql.dgs.exceptions.DgsDataLoaderInstrumentationException
import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.BatchLoaderWithContext
import org.dataloader.DataLoaderRegistry
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import java.util.concurrent.CompletionStage

class DgsDataLoaderInstrumentationDataLoaderCustomizer(
    private val instrumentations: List<DgsDataLoaderInstrumentation>,
) : DgsDataLoaderCustomizer {
    override fun provide(
        original: BatchLoader<*, *>,
        name: String,
    ): Any = throw DgsDataLoaderInstrumentationException(name)

    override fun provide(
        original: BatchLoaderWithContext<*, *>,
        name: String,
    ): Any = BatchLoaderWithContextInstrumentationDriver(original, name, instrumentations)

    override fun provide(
        original: MappedBatchLoader<*, *>,
        name: String,
    ): Any = throw DgsDataLoaderInstrumentationException(name)

    override fun provide(
        original: MappedBatchLoaderWithContext<*, *>,
        name: String,
    ): Any = MappedBatchLoaderWithContextInstrumentationDriver(original, name, instrumentations)
}

internal class BatchLoaderWithContextInstrumentationDriver<K : Any, V>(
    private val original: BatchLoaderWithContext<K, V>,
    private val name: String,
    private val instrumentations: List<DgsDataLoaderInstrumentation>,
) : BatchLoaderWithContext<K, V>,
    DgsDataLoaderRegistryConsumer {
    override fun load(
        keys: List<K>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<List<V>> {
        val contexts = instrumentations.map { it.onDispatch(name, keys, environment) }
        val future = original.load(keys, environment)

        /*
         * DataLoader instrumentation race condition fix:
         *
         * When DataLoader batch operations complete exceptionally very quickly (e.g., throwing immediately
         * in CompletableFuture.supplyAsync), there's a race condition where the CompletableFuture is already
         * completed by the time we attach our whenComplete() callback. In such cases, the callback may not
         * be invoked at all, causing instrumentation events to be missed.
         *
         * This is particularly problematic for:
         * 1. Exception tracking in monitoring/metrics systems
         * 2. Distributed tracing spans that need to be closed properly
         * 3. Any instrumentation that relies on onComplete() being called
         *
         * The solution implements a fallback mechanism that:
         * 1. Tracks whether the normal whenComplete() callback was invoked
         * 2. Checks if the future is already completed but the callback wasn't called
         * 3. Manually triggers the instrumentation in such cases
         *
         * This ensures instrumentation consistency regardless of DataLoader execution timing.
         */
        val cf = future.toCompletableFuture()
        var callbackInvoked = false

        // Use handle() instead of whenComplete() as it's more reliable for exception handling
        val resultFuture =
            future.handle<List<V>> { result, exception ->
                callbackInvoked = true
                try {
                    contexts.asReversed().forEach { c ->
                        c.onComplete(result, exception)
                    }
                } catch (_: Throwable) {
                    // Ignore instrumentation exceptions to prevent interference with business logic
                }

                // Re-throw the exception or return the result
                if (exception != null) {
                    throw if (exception is RuntimeException) exception else RuntimeException(exception)
                }
                result
            }

        // Immediate fallback for race condition: manually trigger instrumentation if callback wasn't invoked
        if (cf.isDone && !callbackInvoked) {
            try {
                val result = if (cf.isCompletedExceptionally) null else cf.get()
                val exception =
                    if (cf.isCompletedExceptionally) {
                        try {
                            cf.get()
                            null
                        } catch (e: Exception) {
                            e.cause ?: e
                        }
                    } else {
                        null
                    }

                contexts.asReversed().forEach { c ->
                    c.onComplete(result, exception)
                }
            } catch (e: Exception) {
                // Fallback exception handling - if we can't extract result/exception properly,
                // at least notify instrumentation that an exception occurred
                contexts.asReversed().forEach { c ->
                    c.onComplete(null, e.cause ?: e)
                }
            }
        } else if (!cf.isDone) {
            // For async completion, add a whenComplete as additional safety net
            resultFuture.whenComplete { _, _ ->
                // This is just a safety net in case handle() somehow doesn't work
                if (!callbackInvoked) {
                    try {
                        val result = if (cf.isCompletedExceptionally) null else cf.get()
                        val exception =
                            if (cf.isCompletedExceptionally) {
                                try {
                                    cf.get()
                                    null
                                } catch (e: Exception) {
                                    e.cause ?: e
                                }
                            } else {
                                null
                            }

                        contexts.asReversed().forEach { c ->
                            c.onComplete(result, exception)
                        }
                    } catch (e: Exception) {
                        contexts.asReversed().forEach { c ->
                            c.onComplete(null, e.cause ?: e)
                        }
                    }
                }
            }
        }

        return resultFuture
    }

    override fun setDataLoaderRegistry(dataLoaderRegistry: DataLoaderRegistry?) {
        if (original is DgsDataLoaderRegistryConsumer) {
            (original as DgsDataLoaderRegistryConsumer).setDataLoaderRegistry(dataLoaderRegistry)
        }
    }
}

internal class MappedBatchLoaderWithContextInstrumentationDriver<K : Any, V>(
    private val original: MappedBatchLoaderWithContext<K, V>,
    private val name: String,
    private val instrumentations: List<DgsDataLoaderInstrumentation>,
) : MappedBatchLoaderWithContext<K, V>,
    DgsDataLoaderRegistryConsumer {
    override fun load(
        keys: Set<K>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<Map<K, V>> {
        val keysList = keys.toList()
        val contexts = instrumentations.map { it.onDispatch(name, keysList, environment) }

        val future = original.load(keys, environment)

        /*
         * Apply the same race condition fix as BatchLoaderWithContextInstrumentationDriver
         * to ensure consistent instrumentation behavior across all DataLoader types.
         */
        val cf = future.toCompletableFuture()
        var whenCompleteInvoked = false

        val resultFuture =
            future.whenComplete { result, exception ->
                whenCompleteInvoked = true
                try {
                    contexts.asReversed().forEach { c ->
                        c.onComplete(result, exception)
                    }
                } catch (_: Throwable) {
                    // Ignore instrumentation exceptions to prevent interference with business logic
                }
            }

        // Fallback for race condition: manually trigger instrumentation if callback wasn't invoked
        if (cf.isDone && !whenCompleteInvoked) {
            try {
                val result = if (cf.isCompletedExceptionally) null else cf.get()
                val exception =
                    if (cf.isCompletedExceptionally) {
                        try {
                            cf.get()
                            null
                        } catch (e: Exception) {
                            e.cause ?: e
                        }
                    } else {
                        null
                    }

                contexts.asReversed().forEach { c ->
                    c.onComplete(result, exception)
                }
            } catch (e: Exception) {
                // Fallback exception handling - if we can't extract result/exception properly,
                // at least notify instrumentation that an exception occurred
                contexts.asReversed().forEach { c ->
                    c.onComplete(null, e.cause ?: e)
                }
            }
        }

        return resultFuture
    }

    override fun setDataLoaderRegistry(dataLoaderRegistry: DataLoaderRegistry?) {
        if (original is DgsDataLoaderRegistryConsumer) {
            (original as DgsDataLoaderRegistryConsumer).setDataLoaderRegistry(dataLoaderRegistry)
        }
    }
}
