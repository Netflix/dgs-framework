/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import com.netflix.graphql.dgs.json.DgsJsonMapper
import graphql.GraphQLContext
import graphql.schema.Coercing
import java.util.Locale

/**
 * Adapts a Jackson 2 [ObjectMapper] to [DgsJsonMapper]. Requires `com.fasterxml.jackson.databind`
 * on the runtime classpath; will fail to load with [NoClassDefFoundError] on a Jackson-3-only
 * classpath.
 */
class Jackson2DgsJsonMapperAdapter(
    val objectMapper: ObjectMapper,
) : DgsJsonMapper {
    override fun writeValueAsString(value: Any): String = objectMapper.writeValueAsString(value)

    override fun <T> readValue(
        content: String,
        clazz: Class<T>,
    ): T = objectMapper.readValue(content, clazz)

    override fun <T> convertValue(
        fromValue: Any,
        toClass: Class<T>,
    ): T = objectMapper.convertValue(fromValue, toClass)

    override fun jsonPathConfiguration(): Configuration =
        Configuration
            .builder()
            .jsonProvider(JacksonJsonProvider(objectMapper))
            .mappingProvider(JacksonMappingProvider(objectMapper))
            .build()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)

    companion object {
        @JvmStatic
        @JvmOverloads
        fun fromOptions(options: DgsGraphQLRequestOptions? = null): Jackson2DgsJsonMapperAdapter =
            Jackson2DgsJsonMapperAdapter(buildObjectMapper(options))

        @JvmStatic
        fun default(): Jackson2DgsJsonMapperAdapter = fromOptions(null)

        private fun buildObjectMapper(options: DgsGraphQLRequestOptions?): ObjectMapper {
            val mapper =
                ObjectMapper()
                    .registerModule(kotlinModule { enable(KotlinFeature.NullIsSameAsDefault) })
                    .registerModule(JavaTimeModule())
                    .registerModule(ParameterNamesModule())
                    .registerModule(Jdk8Module())
                    .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

            options?.scalars?.forEach { (clazz, coercing) ->
                val module = SimpleModule()
                @Suppress("UNCHECKED_CAST")
                module.addSerializer(
                    clazz as Class<Any>,
                    Jackson2CustomScalarSerializer(coercing, options.graphQLContext),
                )
                module.addDeserializer(clazz, Jackson2CustomScalarDeserializer<Any>(coercing, options.graphQLContext))
                mapper.registerModule(module)
            }
            return mapper
        }
    }
}

internal class Jackson2CustomScalarSerializer<T>(
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

internal class Jackson2CustomScalarDeserializer<T>(
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
