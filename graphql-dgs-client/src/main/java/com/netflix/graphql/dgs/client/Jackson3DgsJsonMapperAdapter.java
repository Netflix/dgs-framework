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

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.Jackson3JsonProvider;
import com.jayway.jsonpath.spi.mapper.Jackson3MappingProvider;
import com.netflix.graphql.dgs.json.DgsJsonMapper;
import graphql.GraphQLContext;
import graphql.schema.Coercing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.util.Locale;

/** Adapts a Jackson 3 {@link JsonMapper} to the Jackson-agnostic {@link DgsJsonMapper} contract. */
public class Jackson3DgsJsonMapperAdapter implements DgsJsonMapper {
    private final JsonMapper jsonMapper;

    public Jackson3DgsJsonMapperAdapter(@NotNull JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @NotNull
    public JsonMapper getJsonMapper() {
        return jsonMapper;
    }

    @NotNull
    @Override
    public String writeValueAsString(@NotNull Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    @Override
    public <T> T readValue(@NotNull String content, @NotNull Class<T> clazz) {
        return jsonMapper.readValue(content, clazz);
    }

    @Override
    public <T> T convertValue(@NotNull Object fromValue, @NotNull Class<T> toClass) {
        return jsonMapper.convertValue(fromValue, toClass);
    }

    @NotNull
    @Override
    public Configuration jsonPathConfiguration() {
        return Configuration
                .builder()
                .jsonProvider(new Jackson3JsonProvider(jsonMapper))
                .mappingProvider(new Jackson3MappingProvider(jsonMapper))
                .build()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);
    }

    @NotNull
    public static Jackson3DgsJsonMapperAdapter fromOptions(@Nullable DgsGraphQLRequestOptions options) {
        return new Jackson3DgsJsonMapperAdapter(buildJsonMapper(options));
    }

    @NotNull
    public static Jackson3DgsJsonMapperAdapter fromOptions() {
        return fromOptions(null);
    }

    /** The default Jackson 3 backed mapper. */
    public static Jackson3DgsJsonMapperAdapter defaultMapper() {
        return fromOptions(null);
    }

    @SuppressWarnings("unchecked")
    private static JsonMapper buildJsonMapper(DgsGraphQLRequestOptions options) {
        JsonMapper.Builder builder =
                KotlinModuleSupport
                        .addIfAvailable(JsonMapper.builder())
                        .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

        if (options != null) {
            options.getScalars().forEach((clazz, coercing) -> {
                SimpleModule module = new SimpleModule();
                module.addSerializer(
                        (Class<Object>) clazz,
                        new Jackson3CustomScalarSerializer<>(coercing, options.getGraphQLContext()));
                module.addDeserializer(
                        clazz, new Jackson3CustomScalarDeserializer<>(coercing, options.getGraphQLContext()));
                builder.addModule(module);
            });
        }
        return builder.build();
    }

    static class Jackson3CustomScalarSerializer<T> extends ValueSerializer<T> {
        private final Coercing<?, ?> coercing;
        private final GraphQLContext graphQLContext;

        Jackson3CustomScalarSerializer(Coercing<?, ?> coercing, GraphQLContext graphQLContext) {
            this.coercing = coercing;
            this.graphQLContext = graphQLContext;
        }

        @Override
        public void serialize(T value, JsonGenerator gen, SerializationContext serializers) {
            Object serializedValue = coercing.serialize(value, graphQLContext, Locale.getDefault());
            gen.writeString(serializedValue.toString());
        }
    }

    static class Jackson3CustomScalarDeserializer<T> extends ValueDeserializer<T> {
        private final Coercing<?, ?> coercing;
        private final GraphQLContext graphQLContext;

        Jackson3CustomScalarDeserializer(Coercing<?, ?> coercing, GraphQLContext graphQLContext) {
            this.coercing = coercing;
            this.graphQLContext = graphQLContext;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T deserialize(JsonParser p, DeserializationContext ctxt) {
            JsonNode value = p.readValueAsTree();
            return (T) coercing.parseValue(value.asText(), graphQLContext, Locale.getDefault());
        }
    }
}
