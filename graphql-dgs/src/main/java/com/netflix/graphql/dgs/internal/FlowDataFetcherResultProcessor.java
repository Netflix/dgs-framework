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

import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import graphql.language.OperationDefinition;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.reactive.ReactiveFlowKt;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;

public class FlowDataFetcherResultProcessor implements DataFetcherResultProcessor {
    @Override
    public boolean supportsType(@NotNull Object originalResult) {
        return originalResult instanceof Flow<?>;
    }

    @NotNull
    @Override
    public Object process(@NotNull Object originalResult, @NotNull DgsDataFetchingEnvironment dfe) {
        if (!(originalResult instanceof Flow<?> flow)) {
            throw new IllegalArgumentException("Instance passed to " + getClass().getName()
                    + " was not a Flow<*>. It was a " + originalResult.getClass().getName() + " instead");
        }
        if (dfe.getOperationDefinition().getOperation() == OperationDefinition.Operation.SUBSCRIPTION) {
            return ReactiveFlowKt.asPublisher(flow, kotlin.coroutines.EmptyCoroutineContext.INSTANCE);
        }
        // Keep query and mutation collection off the request thread, matching the former
        // CoroutineScope(Dispatchers.Default).future { flow.toList() } implementation.
        var publisher = ReactiveFlowKt.asPublisher(flow, Dispatchers.getDefault());
        return Flux.from(publisher).collectList().toFuture();
    }
}
