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
import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.json.Jackson3JsonProvider
import com.jayway.jsonpath.spi.mapper.Jackson3MappingProvider
import org.intellij.lang.annotations.Language
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper

/**
 * Representation of a GraphQL response using Jackson 3 for JSON processing.
 * This class gives convenient JSON parsing methods to get data out of the response.
 *
 * Users who want to write Jackson-version-agnostic code should program against the
 * [GraphQLClientResponse] interface.
 */
data class Jackson3GraphQLResponse(
    @Language("json") override val json: String,
    override val headers: Map<String, List<String>>,
    private val mapper: JsonMapper,
) : GraphQLClientResponse {
    /**
     * A JsonPath DocumentContext. Typically, only used internally.
     */
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
    ) : this(
        json,
        headers,
        Jackson3RequestOptions.createJsonMapper(),
    )

    override fun <T> dataAsObject(clazz: Class<T>): T = mapper.convertValue(data, clazz)

    override fun <T> extractValue(path: String): T {
        val dataPath = GraphQLClientResponse.getDataPath(path)

        try {
            return parsed.read(dataPath)
        } catch (ex: Exception) {
            logger.warn("Error extracting path '$path' from data: '$data'")
            throw ex
        }
    }

    override fun <T> extractValueAsObject(
        path: String,
        clazz: Class<T>,
    ): T {
        val dataPath = GraphQLClientResponse.getDataPath(path)

        try {
            return parsed.read(dataPath, clazz)
        } catch (ex: Exception) {
            logger.warn("Error extracting path '$path' from data: '$data'")
            throw ex
        }
    }

    override fun <T> extractValueAsObject(
        path: String,
        typeRef: TypeRef<T>,
    ): T {
        val dataPath = GraphQLClientResponse.getDataPath(path)

        try {
            return parsed.read(dataPath, typeRef)
        } catch (ex: Exception) {
            logger.warn("Error extracting path '$path' from data: '$data'")
            throw ex
        }
    }

    override fun getRequestDetails(): RequestDetails? = extractValueAsObject("gatewayRequestDetails", RequestDetails::class.java)

    override fun hasErrors(): Boolean = errors.isNotEmpty()

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(Jackson3GraphQLResponse::class.java)
    }
}
