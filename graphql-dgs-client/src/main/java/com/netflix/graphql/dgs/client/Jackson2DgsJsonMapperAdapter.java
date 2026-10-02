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

package com.netflix.graphql.dgs.client;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.netflix.graphql.dgs.json.DgsJsonMapper;
import graphql.GraphQLContext;
import graphql.schema.Coercing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Locale;

/**
 * Adapts a Jackson 2 {@link ObjectMapper} to {@link DgsJsonMapper}. Requires
 * {@code com.fasterxml.jackson.databind} on the runtime classpath; will fail to load with
 * {@link NoClassDefFoundError} on a Jackson-3-only classpath.
 */
public class Jackson2DgsJsonMapperAdapter implements DgsJsonMapper {
    private final ObjectMapper objectMapper;

    public Jackson2DgsJsonMapperAdapter(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @NotNull
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @NotNull
    @Override
    public String writeValueAsString(@NotNull Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw Jackson2ExceptionSupport.rethrow(e);
        }
    }

    @Override
    public <T> T readValue(@NotNull String content, @NotNull Class<T> clazz) {
        try {
            return objectMapper.readValue(content, clazz);
        } catch (JsonProcessingException e) {
            throw Jackson2ExceptionSupport.rethrow(e);
        }
    }

    @Override
    public <T> T convertValue(@NotNull Object fromValue, @NotNull Class<T> toClass) {
        return objectMapper.convertValue(fromValue, toClass);
    }

    @NotNull
    @Override
    public Configuration jsonPathConfiguration() {
        return Configuration
                .builder()
                .jsonProvider(new JacksonJsonProvider(objectMapper))
                .mappingProvider(new JacksonMappingProvider(objectMapper))
                .build()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);
    }

    @NotNull
    public static Jackson2DgsJsonMapperAdapter fromOptions(@Nullable DgsGraphQLRequestOptions options) {
        return new Jackson2DgsJsonMapperAdapter(buildObjectMapper(options));
    }

    @NotNull
    public static Jackson2DgsJsonMapperAdapter fromOptions() {
        return fromOptions(null);
    }

    /** The default Jackson 2 backed mapper. */
    public static Jackson2DgsJsonMapperAdapter defaultMapper() {
        return fromOptions(null);
    }

    @SuppressWarnings("unchecked")
    private static ObjectMapper buildObjectMapper(DgsGraphQLRequestOptions options) {
        ObjectMapper mapper =
                KotlinModuleSupport.registerIfAvailable(new ObjectMapper())
                        .registerModule(new JavaTimeModule())
                        .registerModule(new ParameterNamesModule())
                        .registerModule(new Jdk8Module())
                        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        if (options != null) {
            options.getScalars().forEach((clazz, coercing) -> {
                SimpleModule module = new SimpleModule();
                module.addSerializer(
                        (Class<Object>) clazz,
                        new Jackson2CustomScalarSerializer<>(coercing, options.getGraphQLContext()));
                module.addDeserializer(
                        clazz, new Jackson2CustomScalarDeserializer<>(coercing, options.getGraphQLContext()));
                mapper.registerModule(module);
            });
        }
        return mapper;
    }

    static class Jackson2CustomScalarSerializer<T> extends JsonSerializer<T> {
        private final Coercing<?, ?> coercing;
        private final GraphQLContext graphQLContext;

        Jackson2CustomScalarSerializer(Coercing<?, ?> coercing, GraphQLContext graphQLContext) {
            this.coercing = coercing;
            this.graphQLContext = graphQLContext;
        }

        @Override
        public void serialize(T value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            Object serializedValue = coercing.serialize(value, graphQLContext, Locale.getDefault());
            gen.writeString(serializedValue.toString());
        }
    }

    static class Jackson2CustomScalarDeserializer<T> extends JsonDeserializer<T> {
        private final Coercing<?, ?> coercing;
        private final GraphQLContext graphQLContext;

        Jackson2CustomScalarDeserializer(Coercing<?, ?> coercing, GraphQLContext graphQLContext) {
            this.coercing = coercing;
            this.graphQLContext = graphQLContext;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode value = p.readValueAsTree();
            return (T) coercing.parseValue(value.asText(), graphQLContext, Locale.getDefault());
        }
    }
}
