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
import graphql.ExecutionInput;
import graphql.GraphQLContext;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoaderEnvironment;

import java.util.function.Consumer;

/**
 * Context class that is created per request, and is added to both DataFetchingEnvironment and BatchLoaderEnvironment.
 * Custom data can be added by providing a {@link DgsCustomContextBuilder}.
 */
public class DgsContext implements Consumer<GraphQLContext.Builder> {
    private enum GraphQLContextKey {
        DGS_CONTEXT_KEY
    }

    private final Object customContext;
    private final DgsRequestData requestData;

    public DgsContext(Object customContext, DgsRequestData requestData) {
        this.customContext = customContext;
        this.requestData = requestData;
    }

    public Object getCustomContext() {
        return customContext;
    }

    public DgsRequestData getRequestData() {
        return requestData;
    }

    public static DgsContext from(GraphQLContext graphQLContext) {
        return graphQLContext.get(GraphQLContextKey.DGS_CONTEXT_KEY);
    }

    public static DgsContext from(DataFetchingEnvironment dfe) {
        return from(dfe.getGraphQlContext());
    }

    public static DgsContext from(ExecutionInput ei) {
        return from(ei.getGraphQLContext());
    }

    public static DgsContext from(InstrumentationCreateStateParameters p) {
        return from(p.getExecutionInput().getGraphQLContext());
    }

    public static DgsContext from(InstrumentationExecuteOperationParameters p) {
        return from(p.getExecutionContext().getGraphQLContext());
    }

    public static DgsContext from(InstrumentationExecutionParameters p) {
        return from(p.getGraphQLContext());
    }

    public static DgsContext from(InstrumentationExecutionStrategyParameters p) {
        return from(p.getExecutionContext().getGraphQLContext());
    }

    public static DgsContext from(InstrumentationFieldCompleteParameters p) {
        return from(p.getExecutionContext().getGraphQLContext());
    }

    public static DgsContext from(InstrumentationFieldFetchParameters p) {
        return from(p.getExecutionContext().getGraphQLContext());
    }

    public static DgsContext from(InstrumentationFieldParameters p) {
        return from(p.getExecutionContext().getGraphQLContext());
    }

    public static DgsContext from(InstrumentationValidationParameters p) {
        return from(p.getGraphQLContext());
    }

    public static DgsContext from(BatchLoaderEnvironment batchLoaderEnvironment) {
        Object context = batchLoaderEnvironment.getContext();
        if (context instanceof GraphQLContext graphQLContext) {
            return from(graphQLContext);
        }
        if (context instanceof DgsContext dgsContext) {
            return dgsContext;
        }
        throw new RuntimeException(
                "Cannot resolve DgsContext from " + (context != null ? context.getClass().getName() : "null") + ".");
    }

    @SuppressWarnings("unchecked")
    public static <T> T getCustomContext(Object context) {
        if (context instanceof DgsContext dgsContext) {
            return (T) dgsContext.getCustomContext();
        }
        if (context instanceof GraphQLContext graphQLContext) {
            return getCustomContext(from(graphQLContext));
        }
        throw new RuntimeException("The context object passed to getCustomContext is not a DgsContext. It is a "
                + context.getClass().getName() + " instead.");
    }

    public static <T> T getCustomContext(DataFetchingEnvironment dataFetchingEnvironment) {
        DgsContext dgsContext = from(dataFetchingEnvironment);
        return getCustomContext(dgsContext);
    }

    public static <T> T getCustomContext(BatchLoaderEnvironment batchLoaderEnvironment) {
        Object context = batchLoaderEnvironment.getContext();
        if (context == null) {
            throw new RuntimeException("BatchLoaderEnvironment context is null");
        }
        return getCustomContext(context);
    }

    public static DgsRequestData getRequestData(DataFetchingEnvironment dataFetchingEnvironment) {
        return from(dataFetchingEnvironment).getRequestData();
    }

    public static DgsRequestData getRequestData(BatchLoaderEnvironment batchLoaderEnvironment) {
        return from(batchLoaderEnvironment).getRequestData();
    }

    @Override
    public void accept(GraphQLContext.Builder contextBuilder) {
        contextBuilder.put(GraphQLContextKey.DGS_CONTEXT_KEY, this);
    }
}
