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

import graphql.GraphQLContext
import graphql.schema.Coercing
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule
import java.util.Locale

/**
 * Options for GraphQL requests, including custom scalars and GraphQL context, providing a
 * Jackson 3 [JsonMapper] for marshalling and unmarshalling.
 */
class Jackson3RequestOptions(
    val scalars: Map<Class<*>, Coercing<*, *>> = emptyMap(),
    val graphQLContext: GraphQLContext = GraphQLContext.getDefault(),
) {
    constructor() : this(emptyMap())

    constructor(scalars: Map<Class<*>, Coercing<*, *>>) : this(scalars, GraphQLContext.getDefault())

    class CustomScalarDeserializer<T>(
        private val coercing: Coercing<*, *>,
        private val graphQLContext: GraphQLContext,
    ) : ValueDeserializer<T>() {
        @Suppress("UNCHECKED_CAST")
        override fun deserialize(
            p: JsonParser,
            ctxt: DeserializationContext,
        ): T {
            val value = p.readValueAsTree<JsonNode>()
            return coercing.parseValue(value.asText(), graphQLContext, Locale.getDefault()) as T
        }
    }

    class CustomScalarSerializer<T>(
        private val coercing: Coercing<*, *>,
        private val graphQLContext: GraphQLContext,
    ) : ValueSerializer<T>() {
        override fun serialize(
            value: T & Any,
            gen: JsonGenerator,
            serializers: SerializationContext,
        ) {
            val serializedValue = coercing.serialize(value as Any, graphQLContext, Locale.getDefault())
            gen.writeString(serializedValue.toString())
        }
    }

    companion object {
        /**
         * Create a Jackson 3 JsonMapper configured with KotlinModule, enum default values,
         * and optional custom scalar serializers.
         */
        fun createJsonMapper(options: Jackson3RequestOptions? = null): JsonMapper {
            val builder =
                JsonMapper
                    .builder()
                    .addModule(KotlinModule.Builder().enable(KotlinFeature.NullIsSameAsDefault).build())
                    .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)

            options?.scalars?.forEach { (clazz, coercing) ->
                val module = SimpleModule()
                @Suppress("UNCHECKED_CAST")
                module.addSerializer(
                    clazz as Class<Any>,
                    CustomScalarSerializer<Any>(coercing, options.graphQLContext),
                )
                module.addDeserializer(
                    clazz,
                    CustomScalarDeserializer<Any>(
                        coercing,
                        options.graphQLContext,
                    ),
                )
                builder.addModule(module)
            }
            return builder.build()
        }

        @JvmStatic
        fun createJsonMapper(): JsonMapper = createJsonMapper(null)
    }
}
