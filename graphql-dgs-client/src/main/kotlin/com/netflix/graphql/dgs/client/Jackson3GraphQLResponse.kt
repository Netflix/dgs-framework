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

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.Jackson3JsonProvider
import com.jayway.jsonpath.spi.mapper.Jackson3MappingProvider
import org.intellij.lang.annotations.Language
import tools.jackson.databind.json.JsonMapper

/**
 * Jackson 3 implementation of [GraphQLClientResponse].
 * Extraction methods are inherited from the interface defaults.
 */
data class Jackson3GraphQLResponse(
    @Language("json") override val json: String,
    override val headers: Map<String, List<String>>,
    private val mapper: JsonMapper,
) : GraphQLClientResponse {
    override val parsed: DocumentContext =
        JsonPath
            .using(
                Configuration
                    .builder()
                    .jsonProvider(Jackson3JsonProvider(mapper))
                    .mappingProvider(Jackson3MappingProvider(mapper))
                    .build()
                    .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL),
            ).parse(json)

    override val data: Map<String, Any> = parsed.read("data") ?: emptyMap()
    override val errors: List<GraphQLError> = parsed.read("errors", jsonTypeRef<List<GraphQLError>>()) ?: emptyList()

    constructor(
        @Language("json") json: String,
    ) : this(json, emptyMap())

    constructor(
        @Language("json") json: String,
        headers: Map<String, List<String>>,
    ) : this(json, headers, Jackson3RequestOptions.createJsonMapper())

    override fun <T> dataAsObject(clazz: Class<T>): T = mapper.convertValue(data, clazz)
}
