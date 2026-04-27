/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.netflix.graphql.dgs.client

import graphql.GraphQLContext
import graphql.schema.Coercing

/**
 * Jackson-agnostic request options for DGS GraphQL clients. Consumed by adapters
 * (e.g. [Jackson2DgsJsonMapperAdapter.fromOptions]) to build a [com.netflix.graphql.dgs.json.DgsJsonMapper].
 */
class DgsGraphQLRequestOptions
    @JvmOverloads
    constructor(
        val scalars: Map<Class<*>, Coercing<*, *>> = emptyMap(),
        val graphQLContext: GraphQLContext = GraphQLContext.getDefault(),
    )
