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

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import graphql.GraphQLContext
import graphql.schema.Coercing
import java.util.Locale

/**
 * Options for GraphQL requests, including custom scalars and GraphQL context and providing unified
 * ObjectMapper for marshalling and unmarshalling.
 */
class GraphQLRequestOptions(
    val scalars: Map<Class<*>, Coercing<*, *>> = emptyMap(),
    val graphQLContext: GraphQLContext = GraphQLContext.getDefault(),
) {
    constructor() : this(emptyMap())

    constructor(scalars: Map<Class<*>, Coercing<*, *>>) : this(scalars, GraphQLContext.getDefault())

    /**
     * Helper class to wrap a scalar deserializer into a Jackson JsonDeserializer
     */
    class CustomScalarDeserializer<T>(
        private val coercing: Coercing<*, *>,
        private val graphQLContext: GraphQLContext,
    ) : JsonDeserializer<T>() {
        @Suppress("UNCHECKED_CAST")
        override fun deserialize(
            p: JsonParser,
            ctxt: DeserializationContext,
        ): T {
            val value = p.readValueAsTree<JsonNode>()
            return coercing.parseValue(value.asText(), graphQLContext, Locale.getDefault()) as T
        }
    }

    /**
     * Helper class to wrap a scalar serialization into a Jackson JsonSerializer
     */
    class CustomScalarSerializer<T>(
        private val coercing: Coercing<*, *>,
        private val graphQLContext: GraphQLContext,
    ) : JsonSerializer<T>() {
        override fun serialize(
            value: T,
            gen: JsonGenerator,
            serializers: SerializerProvider,
        ) {
            val serializedValue = coercing.serialize(value as Any, graphQLContext, Locale.getDefault())
            gen.writeString(serializedValue.toString())
        }
    }

    companion object {
        fun createCustomObjectMapper(options: GraphQLRequestOptions? = null): ObjectMapper {
            val mapper = ObjectMapper()
            mapper.registerModule(kotlinModule { enable(KotlinFeature.NullIsSameAsDefault) })
            mapper.registerModule(JavaTimeModule())
            mapper.registerModule(ParameterNamesModule())
            mapper.registerModule(Jdk8Module())
            mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)

            // Register custom deserializers if scalars are provided
            options?.scalars?.forEach { (clazz, coercing) ->
                val module = SimpleModule()
                module.addSerializer(
                    clazz,
                    CustomScalarSerializer(coercing, options.graphQLContext),
                )
                module.addDeserializer(
                    clazz,
                    CustomScalarDeserializer(
                        coercing,
                        options.graphQLContext,
                    ),
                )
                mapper.registerModule(module)
            }
            return mapper
        }

        // Overloaded method for Java compatibility
        @JvmStatic
        fun createCustomObjectMapper(): ObjectMapper = createCustomObjectMapper(null)
    }
}
