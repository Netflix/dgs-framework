/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.netflix.graphql.dgs.client

import org.intellij.lang.annotations.Language
import reactor.core.publisher.Flux

/**
 * Jackson-agnostic reactive GraphQL client contract for clients that produce multiple results
 * such as subscriptions. Implemented by the new `Dgs*` subscription clients and (for back-compat)
 * by the deprecated [ReactiveGraphQLClient].
 */
interface DgsReactiveGraphQLClient {
    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
    ): Flux<out DgsGraphQLResponse>

    fun reactiveExecuteQuery(
        @Language("graphql") query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): Flux<out DgsGraphQLResponse>
}
