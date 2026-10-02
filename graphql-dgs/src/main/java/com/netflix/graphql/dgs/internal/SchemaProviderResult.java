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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class SchemaProviderResult {
    private final GraphQLSchema graphQLSchema;
    private final RuntimeWiring runtimeWiring;

    public SchemaProviderResult(@NotNull GraphQLSchema graphQLSchema, @NotNull RuntimeWiring runtimeWiring) {
        this.graphQLSchema = graphQLSchema;
        this.runtimeWiring = runtimeWiring;
    }

    @NotNull
    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    @NotNull
    public RuntimeWiring getRuntimeWiring() {
        return runtimeWiring;
    }

    @NotNull
    public GraphQLSchema component1() {
        return graphQLSchema;
    }

    @NotNull
    public RuntimeWiring component2() {
        return runtimeWiring;
    }

    @NotNull
    public SchemaProviderResult copy(@NotNull GraphQLSchema graphQLSchema, @NotNull RuntimeWiring runtimeWiring) {
        return new SchemaProviderResult(graphQLSchema, runtimeWiring);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof SchemaProviderResult that
                && Objects.equals(graphQLSchema, that.graphQLSchema)
                && Objects.equals(runtimeWiring, that.runtimeWiring);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(graphQLSchema);
        result = 31 * result + Objects.hashCode(runtimeWiring);
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return "SchemaProviderResult(graphQLSchema=" + graphQLSchema + ", runtimeWiring=" + runtimeWiring + ")";
    }
}
