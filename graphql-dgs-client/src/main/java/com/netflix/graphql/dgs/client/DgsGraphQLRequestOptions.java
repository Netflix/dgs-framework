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

package com.netflix.graphql.dgs.client;

import graphql.GraphQLContext;
import graphql.schema.Coercing;

import java.util.Map;

/**
 * Jackson-agnostic request options for DGS GraphQL clients. Consumed by adapters
 * (e.g. {@link Jackson3DgsJsonMapperAdapter#fromOptions}) to build a
 * {@link com.netflix.graphql.dgs.json.DgsJsonMapper}.
 */
public class DgsGraphQLRequestOptions {
    private final Map<Class<?>, Coercing<?, ?>> scalars;
    private final GraphQLContext graphQLContext;

    public DgsGraphQLRequestOptions(Map<Class<?>, Coercing<?, ?>> scalars, GraphQLContext graphQLContext) {
        this.scalars = scalars;
        this.graphQLContext = graphQLContext;
    }

    public DgsGraphQLRequestOptions(Map<Class<?>, Coercing<?, ?>> scalars) {
        this(scalars, GraphQLContext.getDefault());
    }

    public DgsGraphQLRequestOptions() {
        this(Map.of(), GraphQLContext.getDefault());
    }

    public Map<Class<?>, Coercing<?, ?>> getScalars() {
        return scalars;
    }

    public GraphQLContext getGraphQLContext() {
        return graphQLContext;
    }
}
