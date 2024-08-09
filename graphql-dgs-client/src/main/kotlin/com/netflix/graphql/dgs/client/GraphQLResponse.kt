/*
 * Copyright 2021 Netflix, Inc.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import org.intellij.lang.annotations.Language
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Representation of a GraphQL response, which may contain GraphQL errors.
 * This class gives convenient JSON parsing methods to get data out of the response.
 */
data class GraphQLResponse(
    @Language("json") val json: String,
    val headers: Map<String, List<String>>,
    private val mapper: ObjectMapper,
) {
    /**
     * A JsonPath DocumentContext. Typically, only used internally.
     */
    val parsed: DocumentContext =
        JsonPath
            .using(
                Configuration
                    .builder()
                    .jsonProvider(JacksonJsonProvider(mapper))
                    .mappingProvider(JacksonMappingProvider(mapper))
                    .build()
                    .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL),
            ).parse(json)

    /**
     * Map representation of data
     */

    val data: Map<String, Any> = parsed.read("data") ?: emptyMap()
    val errors: List<GraphQLError> = parsed.read("errors", jsonTypeRef<List<GraphQLError>>()) ?: emptyList()

    constructor(
        @Language("json") json: String,
    ) : this(json, emptyMap())
    constructor(
        @Language("json") json: String,
        headers: Map<String, List<String>>,
    ) : this(
        json,
        headers,
        // default object mapper instead no instance is passed in the constructor
        DEFAULT_MAPPER,
    )

    /**
     * Deserialize data into the given class.
     * The class may need Jackson annotations for correct mapping.
     */
    fun <T> dataAsObject(clazz: Class<T>): T = mapper.convertValue(data, clazz)

    /**
     * Extract values given a JsonPath. The return type will be whatever type you expect.
     * Although this looks type safe, it really isn't. Make sure values map to the expected type.
     * For JSON objects, a Map is returned. If you want to deserialize to a class, use #extractValueAsObject instead.
     */
    fun <T> extractValue(path: String): T {
        val dataPath = getDataPath(path)

        try {
            return parsed.read(dataPath)
        } catch (ex: Exception) {
            logger.warn("Error extracting path '$path' from data: '$data'")
            throw ex
        }
    }

    /**
     * Extract values given a JsonPath and deserialize into the given class.
     */
    fun <T> extractValueAsObject(
        path: String,
        clazz: Class<T>,
    ): T {
        val dataPath = getDataPath(path)

        try {
            return parsed.read(dataPath, clazz)
        } catch (ex: Exception) {
            logger.warn("Error extracting path '$path' from data: '$data'")
            throw ex
        }
    }

    /**
     * Extract values given a JsonPath and deserialize into the given TypeRef.
     * Use this for Lists of a specific type.
     */
    fun <T> extractValueAsObject(
        path: String,
        typeRef: TypeRef<T>,
    ): T {
        val dataPath = getDataPath(path)

        try {
            return parsed.read(dataPath, typeRef)
        } catch (ex: Exception) {
            logger.warn("Error extracting path '$path' from data: '$data'")
            throw ex
        }
    }

    /**
     * Extracts RequestDetails from the response if available.
     * Returns null otherwise.
     */
    fun getRequestDetails(): RequestDetails? = extractValueAsObject("gatewayRequestDetails", RequestDetails::class.java)

    fun hasErrors(): Boolean = errors.isNotEmpty()

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GraphQLResponse::class.java)

        internal val DEFAULT_MAPPER: ObjectMapper =
            jsonMapper {
                addModule(kotlinModule { enable(KotlinFeature.NullIsSameAsDefault) })
                addModule(JavaTimeModule())
                addModule(ParameterNamesModule())
                addModule(Jdk8Module())
                enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
            }

        fun getDataPath(path: String): String =
            if (path == "data" || path.startsWith("data.")) {
                path
            } else {
                "data.$path"
            }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RequestDetails(
    val requestId: String?,
    val edgarLink: String?,
)

inline fun <reified T> jsonTypeRef(): TypeRef<T> = object : TypeRef<T>() {}
