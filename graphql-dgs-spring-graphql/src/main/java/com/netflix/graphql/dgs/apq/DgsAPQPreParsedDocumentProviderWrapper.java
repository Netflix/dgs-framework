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

package com.netflix.graphql.dgs.apq;

import graphql.ExecutionInput;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.execution.preparsed.persisted.ApolloPersistedQuerySupport;
import graphql.execution.preparsed.persisted.PersistedQueryCache;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DgsAPQPreParsedDocumentProviderWrapper extends ApolloPersistedQuerySupport {
    private final Optional<PreparsedDocumentProvider> preparsedDocumentProvider;

    public DgsAPQPreParsedDocumentProviderWrapper(
            PersistedQueryCache persistedQueryCache, Optional<PreparsedDocumentProvider> preparsedDocumentProvider) {
        super(persistedQueryCache);
        this.preparsedDocumentProvider = preparsedDocumentProvider;
    }

    @Override
    public CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(
            ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        Optional<Object> queryId = getPersistedQueryId(executionInput);
        if (queryId.isPresent()) {
            return super.getDocumentAsync(executionInput, parseAndValidateFunction);
        }

        if (preparsedDocumentProvider.isPresent()) {
            return preparsedDocumentProvider.get().getDocumentAsync(executionInput, parseAndValidateFunction);
        }

        return CompletableFuture.completedFuture(parseAndValidateFunction.apply(executionInput));
    }
}
