/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.graphql.dgs.client

import org.intellij.lang.annotations.Language

/**
 * Jackson-version-agnostic interface for blocking GraphQL clients.
 * Returns [GraphQLClientResponse] which works with both Jackson 2 and Jackson 3.
 *
 * Both [GraphQLClient] (Jackson 2) and the Jackson 3 client classes implement this interface,
 * allowing users to write code that is independent of the Jackson version.
 */
interface DgsGraphQLClient {
    fun executeQuery(
        @Language("graphql") query: String,
    ): GraphQLClientResponse

    fun executeQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
    ): GraphQLClientResponse

    fun executeQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): GraphQLClientResponse
}
