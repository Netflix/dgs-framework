/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.netflix.graphql.dgs.client

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.Jackson3JsonProvider
import com.jayway.jsonpath.spi.mapper.Jackson3MappingProvider
import com.netflix.graphql.dgs.json.DgsJsonMapper
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
 * Adapts a Jackson 3 [JsonMapper] to the Jackson-agnostic [DgsJsonMapper] contract.
 */
class Jackson3DgsJsonMapperAdapter(
    val jsonMapper: JsonMapper,
) : DgsJsonMapper {
    override fun writeValueAsString(value: Any): String = jsonMapper.writeValueAsString(value)

    override fun <T> readValue(
        content: String,
        clazz: Class<T>,
    ): T = jsonMapper.readValue(content, clazz)

    override fun <T> convertValue(
        fromValue: Any,
        toClass: Class<T>,
    ): T = jsonMapper.convertValue(fromValue, toClass)

    override fun jsonPathConfiguration(): Configuration =
        Configuration
            .builder()
            .jsonProvider(Jackson3JsonProvider(jsonMapper))
            .mappingProvider(Jackson3MappingProvider(jsonMapper))
            .build()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)

    companion object {
        @JvmStatic
        @JvmOverloads
        fun fromOptions(options: DgsGraphQLRequestOptions? = null): Jackson3DgsJsonMapperAdapter =
            Jackson3DgsJsonMapperAdapter(buildJsonMapper(options))

        @JvmStatic
        fun default(): Jackson3DgsJsonMapperAdapter = fromOptions(null)

        private fun buildJsonMapper(options: DgsGraphQLRequestOptions?): JsonMapper {
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
                    Jackson3CustomScalarSerializer(coercing, options.graphQLContext),
                )
                module.addDeserializer(clazz, Jackson3CustomScalarDeserializer<Any>(coercing, options.graphQLContext))
                builder.addModule(module)
            }
            return builder.build()
        }
    }
}

internal class Jackson3CustomScalarSerializer<T>(
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

internal class Jackson3CustomScalarDeserializer<T>(
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
