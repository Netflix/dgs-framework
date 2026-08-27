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

import org.intellij.lang.annotations.Language;

import java.util.Map;

/**
 * Jackson-agnostic blocking GraphQL client contract. Implemented by the new {@code Dgs*} client
 * classes and (for back-compat) by the deprecated {@link GraphQLClient}.
 */
public interface DgsGraphQLClient {
    DgsGraphQLResponse executeQuery(@Language("graphql") String query);

    DgsGraphQLResponse executeQuery(@Language("graphql") String query, Map<String, Object> variables);

    DgsGraphQLResponse executeQuery(
            @Language("graphql") String query, Map<String, Object> variables, String operationName);
}
