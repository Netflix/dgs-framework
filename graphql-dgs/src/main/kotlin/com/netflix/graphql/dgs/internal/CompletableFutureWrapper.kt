/*
 * Copyright 2023 Netflix, Inc.
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

import org.reactivestreams.Publisher
import org.springframework.core.task.AsyncTaskExecutor
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf

internal class CompletableFutureWrapper(private val taskExecutor: AsyncTaskExecutor?) {
    private val supportsReactor: Boolean = try {
        Class.forName("org.reactivestreams.Publisher")
        true
    } catch (ex: Exception) {
        false
    }

    /**
     * Wrap the call to a data fetcher in CompletableFuture to enable parallel behavior.
     * Used when virtual threads are enabled.
     */
    fun wrapInCompletableFuture(function: () -> Any?): Any? {
        return CompletableFuture.supplyAsync({
            return@supplyAsync function.invoke()
        }, taskExecutor)
    }

    /**
     * Decides if a data fetcher method should be wrapped in CompletableFuture automatically.
     * This is only done when a taskExecutor is available, and if the data fetcher doesn't explicitly return CompletableFuture already.
     * Used when virtual threads are enabled.
     */
    fun shouldWrapInCompletableFuture(kFunc: KFunction<*>): Boolean {
        return taskExecutor != null &&
            !kFunc.returnType.isSubtypeOf(typeOf<CompletionStage<*>>()) &&
            !isReactive(kFunc.returnType)
    }

    private fun isReactive(returnType: KType): Boolean {
        return supportsReactor && returnType.isSubtypeOf(typeOf<Publisher<*>>())
    }

    /**
     * Decides if a data fetcher method should be wrapped in CompletableFuture automatically.
     * This is only done when a taskExecutor is available, and if the data fetcher doesn't explicitly return CompletableFuture already.
     * Used when virtual threads are enabled.
     */
    fun shouldWrapInCompletableFuture(method: Method): Boolean {
        return taskExecutor != null &&
            !CompletionStage::class.java.isAssignableFrom(method.returnType) &&
            !isReactive(method.returnType)
    }

    private fun isReactive(returnType: Class<*>): Boolean {
        return supportsReactor && Publisher::class.java.isAssignableFrom(returnType)
    }
}
