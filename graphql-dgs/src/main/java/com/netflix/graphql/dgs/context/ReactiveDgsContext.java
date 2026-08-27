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

package com.netflix.graphql.dgs.context;

import com.netflix.graphql.dgs.internal.DgsRequestData;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

public class ReactiveDgsContext extends DgsContext {
    private final ContextView reactorContext;

    public ReactiveDgsContext(Object customContext, DgsRequestData requestData, ContextView reactorContext) {
        super(customContext, requestData);
        this.reactorContext = reactorContext;
    }

    public ReactiveDgsContext(Object customContext, DgsRequestData requestData) {
        this(customContext, requestData, Context.empty());
    }

    public ReactiveDgsContext(DgsRequestData requestData) {
        this(null, requestData, Context.empty());
    }

    public ContextView getReactorContext() {
        return reactorContext;
    }

    public static ReactiveDgsContext from(GraphQLContext graphQLContext) {
        DgsContext dgsContext = DgsContext.from(graphQLContext);
        return dgsContext instanceof ReactiveDgsContext reactiveDgsContext ? reactiveDgsContext : null;
    }

    public static ReactiveDgsContext from(DataFetchingEnvironment dfe) {
        return from(dfe.getGraphQlContext());
    }
}
