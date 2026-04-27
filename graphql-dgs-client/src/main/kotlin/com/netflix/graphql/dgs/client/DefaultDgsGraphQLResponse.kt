/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.netflix.graphql.dgs.client

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.netflix.graphql.dgs.json.DgsJsonMapper
import org.intellij.lang.annotations.Language

class DefaultDgsGraphQLResponse(
    @Language("json") override val json: String,
    override val headers: Map<String, List<String>>,
    private val mapper: DgsJsonMapper,
) : DgsGraphQLResponse {
    override val parsed: DocumentContext = JsonPath.using(mapper.jsonPathConfiguration()).parse(json)

    override val data: Map<String, Any> = parsed.read("data") ?: emptyMap()
    override val errors: List<GraphQLError> = parsed.read("errors", jsonTypeRef<List<GraphQLError>>()) ?: emptyList()

    override fun <T> dataAsObject(clazz: Class<T>): T = mapper.convertValue(data, clazz)
}
