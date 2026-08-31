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
import com.netflix.graphql.dgs.context.ReactiveDgsContext;
import graphql.language.OperationDefinition;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

public class MonoDataFetcherResultProcessor implements DataFetcherResultProcessor {
    @Override
    public boolean supportsType(Object originalResult) {
        return originalResult instanceof Mono<?>;
    }

    @Override
    public Object process(Object originalResult, DgsDataFetchingEnvironment dfe) {
        if (!(originalResult instanceof Mono<?> mono)) {
            throw new IllegalArgumentException("Instance passed to " + getClass().getName()
                    + " was not a Mono<*>. It was a " + originalResult.getClass().getName() + " instead");
        }
        if (dfe.getOperationDefinition().getOperation() == OperationDefinition.Operation.SUBSCRIPTION) {
            return mono;
        }
        return mono.contextWrite(ReactiveContexts.from(dfe)).toFuture();
    }

    static final class ReactiveContexts {
        private ReactiveContexts() {
        }

        static ContextView from(DgsDataFetchingEnvironment dfe) {
            ReactiveDgsContext context = ReactiveDgsContext.from(dfe);
            ContextView reactorContext = context != null ? context.getReactorContext() : null;
            return reactorContext != null ? reactorContext : Context.empty();
        }
    }
}
