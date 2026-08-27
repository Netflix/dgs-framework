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

package com.netflix.graphql.dgs.internal;

import kotlin.reflect.KFunction;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.reactivestreams.Publisher;
import org.springframework.core.task.AsyncTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

class CompletableFutureWrapper {
    private final AsyncTaskExecutor taskExecutor;

    private final boolean supportsReactor;

    CompletableFutureWrapper(AsyncTaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
        boolean reactorAvailable;
        try {
            Class.forName("org.reactivestreams.Publisher");
            reactorAvailable = true;
        } catch (Exception ex) {
            reactorAvailable = false;
        }
        this.supportsReactor = reactorAvailable;
    }

    /**
     * Wrap the call to a data fetcher in CompletableFuture to enable parallel behavior.
     * Used when virtual threads are enabled.
     */
    CompletableFuture<Object> wrapInCompletableFuture(Supplier<Object> function) {
        return CompletableFuture.supplyAsync(function, taskExecutor);
    }

    /**
     * Decides if a data fetcher method should be wrapped in CompletableFuture automatically.
     * This is only done when a taskExecutor is available, and if the data fetcher doesn't explicitly return
     * CompletableFuture already. Used when virtual threads are enabled.
     */
    boolean shouldWrapInCompletableFuture(KFunction<?> kFunc) {
        if (taskExecutor == null) {
            return false;
        }
        Method javaMethod = ReflectJvmMapping.getJavaMethod(kFunc);
        if (javaMethod == null) {
            return true;
        }
        return shouldWrapInCompletableFuture(javaMethod);
    }

    /**
     * Decides if a data fetcher method should be wrapped in CompletableFuture automatically.
     * This is only done when a taskExecutor is available, and if the data fetcher doesn't explicitly return
     * CompletableFuture already. Used when virtual threads are enabled.
     */
    boolean shouldWrapInCompletableFuture(Method method) {
        return taskExecutor != null
                && !CompletionStage.class.isAssignableFrom(method.getReturnType())
                && !isReactive(method.getReturnType());
    }

    private boolean isReactive(Class<?> returnType) {
        return supportsReactor && Publisher.class.isAssignableFrom(returnType);
    }
}
