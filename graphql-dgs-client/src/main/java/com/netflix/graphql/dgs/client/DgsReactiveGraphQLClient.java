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
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Jackson-agnostic reactive GraphQL client contract for clients that produce multiple results
 * such as subscriptions. Implemented by the new {@code Dgs*} subscription clients and (for back-compat)
 * by the deprecated {@link ReactiveGraphQLClient}.
 */
public interface DgsReactiveGraphQLClient {
    Flux<? extends DgsGraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query, Map<String, Object> variables);

    Flux<? extends DgsGraphQLResponse> reactiveExecuteQuery(
            @Language("graphql") String query, Map<String, Object> variables, String operationName);
}
