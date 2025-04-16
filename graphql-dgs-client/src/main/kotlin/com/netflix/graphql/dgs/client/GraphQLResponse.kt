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

package com.netflix.graphql.dgs.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import graphql.GraphQLContext
import graphql.schema.Coercing
import org.intellij.lang.annotations.Language
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Locale

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
        null,
    )
    constructor(
        @Language("json") json: String,
        headers: Map<String, List<String>>,
        options: GraphQLResponseOptions? = null,
    ) : this(
        json,
        headers,
        createCustomObjectMapper(options),
    )

    class GraphQLResponseOptions(
        val scalars: Map<Class<*>, Coercing<*, *>> = emptyMap(),
        val graphQLContext: GraphQLContext = GraphQLContext.getDefault(),
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

    /**
     * Helper class to wrap a scalar deserializer into a Jackson JsonDeserializer
     */
    class CustomScalarDeserializer<T>(
        private val coercing: Coercing<*, *>,
        private val graphQLContext: GraphQLContext,
    ) : JsonDeserializer<T>() {
        override fun deserialize(
            p: JsonParser,
            ctxt: DeserializationContext,
        ): T {
            val value = p.readValueAsTree<JsonNode>()
            return coercing.parseValue(value.asText(), graphQLContext, Locale.getDefault()) as T
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GraphQLResponse::class.java)

        fun createCustomObjectMapper(options: GraphQLResponseOptions? = null): ObjectMapper {
            val mapper = ObjectMapper()
            mapper.registerModule(kotlinModule { enable(KotlinFeature.NullIsSameAsDefault) })
            mapper.registerModule(JavaTimeModule())
            mapper.registerModule(ParameterNamesModule())
            mapper.registerModule(Jdk8Module())
            mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)

            // Register custom deserializers if scalars are provided
            options?.scalars?.forEach { (clazz, coercing) ->
                mapper.registerModule(SimpleModule().addDeserializer(clazz, CustomScalarDeserializer(coercing, options.graphQLContext)))
            }
            return mapper
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
