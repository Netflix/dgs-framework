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

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;

import java.util.Objects;

public final class SchemaProviderResult {
    private final GraphQLSchema graphQLSchema;
    private final RuntimeWiring runtimeWiring;

    public SchemaProviderResult(GraphQLSchema graphQLSchema, RuntimeWiring runtimeWiring) {
        this.graphQLSchema = graphQLSchema;
        this.runtimeWiring = runtimeWiring;
    }

    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    public RuntimeWiring getRuntimeWiring() {
        return runtimeWiring;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof SchemaProviderResult that
                && Objects.equals(graphQLSchema, that.graphQLSchema)
                && Objects.equals(runtimeWiring, that.runtimeWiring);
    }

    @Override
    public int hashCode() {
        return Objects.hash(graphQLSchema, runtimeWiring);
    }

    @Override
    public String toString() {
        return "SchemaProviderResult(graphQLSchema=" + graphQLSchema + ", runtimeWiring=" + runtimeWiring + ")";
    }
}
