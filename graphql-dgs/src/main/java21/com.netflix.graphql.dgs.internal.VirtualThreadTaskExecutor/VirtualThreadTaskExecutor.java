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

package com.netflix.graphql.dgs.internal;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.lang.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;

/**
 * AsyncTaskExecutor based on Virtual Threads.
 * JDK21+ only.
 */
@SuppressWarnings("unused")
public class VirtualThreadTaskExecutor implements AsyncTaskExecutor {
    private final ThreadFactory threadFactory;

    @Nullable
    private final ContextSnapshotFactory contextSnapshotFactory;

    public VirtualThreadTaskExecutor(@Nullable ContextSnapshotFactory contextSnapshotFactory) {
        this.contextSnapshotFactory = contextSnapshotFactory;
        this.threadFactory = Thread.ofVirtual().name("dgs-virtual-thread-", 0).factory();
    }

    @Override
    public void execute(@NotNull Runnable task) {
        if (contextSnapshotFactory != null) {
            ContextSnapshot contextSnapshot = contextSnapshotFactory.captureAll();
            var wrapped = contextSnapshot.wrap(task);
            threadFactory.newThread(wrapped).start();
        } else {
            threadFactory.newThread(task).start();
        }
    }

    @Override
    @Deprecated
    public void execute(@NotNull Runnable task, long startTimeout) {
        var future = new FutureTask<>(task, null);
        execute(future);
    }

    @NotNull
    @Override
    public Future<?> submit(@NotNull Runnable task) {
        var future = new FutureTask<>(task, null);
        execute(future);
        return future;
    }

    @NotNull
    @Override
    public <T> Future<T> submit(@NotNull Callable<T> task) {
        var future = new FutureTask<>(task);
        execute(future);
        return future;
    }
}