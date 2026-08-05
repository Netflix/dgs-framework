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
import reactor.core.publisher.Mono

/**
 * Jackson-agnostic reactive GraphQL client contract. Implemented by the new `Dgs*` client
 * classes and (for back-compat) by the deprecated [MonoGraphQLClient].
 */
interface DgsMonoGraphQLClient {
    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
    ): Mono<out DgsGraphQLResponse>

    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
    ): Mono<out DgsGraphQLResponse>

    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): Mono<out DgsGraphQLResponse>
}
