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
import reactor.core.publisher.Flux;

public class FluxDataFetcherResultProcessor implements DataFetcherResultProcessor {
    @Override
    public boolean supportsType(Object originalResult) {
        return originalResult instanceof Flux<?>;
    }

    @Override
    public Object process(Object originalResult, DgsDataFetchingEnvironment dfe) {
        if (!(originalResult instanceof Flux<?> flux)) {
            throw new IllegalArgumentException("Instance passed to " + getClass().getName()
                    + " was not a Flux<*>. It was a " + originalResult.getClass().getName() + " instead");
        }
        if (dfe.getOperationDefinition().getOperation() == OperationDefinition.Operation.SUBSCRIPTION) {
            return flux;
        }
        return flux.contextWrite(MonoDataFetcherResultProcessor.ReactiveContexts.from(dfe))
                .collectList()
                .toFuture();
    }
}
