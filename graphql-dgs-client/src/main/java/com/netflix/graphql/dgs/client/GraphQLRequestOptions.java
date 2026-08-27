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
import graphql.GraphQLContext;
import graphql.schema.Coercing;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Options for GraphQL requests, including custom scalars and GraphQL context and providing unified
 * ObjectMapper for marshalling and unmarshalling.
 *
 * @deprecated Tied to Jackson 2 via createCustomObjectMapper() and the nested scalar serializers.
 *             Use {@link DgsGraphQLRequestOptions}, which is Jackson-agnostic.
 *             This class will be removed in a future release.
 */
@Deprecated
public class GraphQLRequestOptions {
    private final Map<Class<?>, Coercing<?, ?>> scalars;
    private final GraphQLContext graphQLContext;

    public GraphQLRequestOptions(Map<Class<?>, Coercing<?, ?>> scalars, GraphQLContext graphQLContext) {
        this.scalars = scalars;
        this.graphQLContext = graphQLContext;
    }

    public GraphQLRequestOptions(Map<Class<?>, Coercing<?, ?>> scalars) {
        this(scalars, GraphQLContext.getDefault());
    }

    public GraphQLRequestOptions() {
        this(Map.of(), GraphQLContext.getDefault());
    }

    public Map<Class<?>, Coercing<?, ?>> getScalars() {
        return scalars;
    }

    public GraphQLContext getGraphQLContext() {
        return graphQLContext;
    }

    @SuppressWarnings("unchecked")
    public static ObjectMapper createCustomObjectMapper(GraphQLRequestOptions options) {
        ObjectMapper mapper = new ObjectMapper();
        KotlinModuleSupport.registerIfAvailable(mapper);
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new ParameterNamesModule());
        mapper.registerModule(new Jdk8Module());
        mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);

        // Register custom serializers/deserializers if scalars are provided
        if (options != null) {
            options.getScalars().forEach((clazz, coercing) -> {
                SimpleModule module = new SimpleModule();
                module.addSerializer(
                        (Class<Object>) clazz, new CustomScalarSerializer<>(coercing, options.getGraphQLContext()));
                module.addDeserializer(clazz, new CustomScalarDeserializer<>(coercing, options.getGraphQLContext()));
                mapper.registerModule(module);
            });
        }
        return mapper;
    }

    public static ObjectMapper createCustomObjectMapper() {
        return createCustomObjectMapper(null);
    }

    /** Helper class to wrap a scalar deserializer into a Jackson JsonDeserializer. */
    public static class CustomScalarDeserializer<T> extends JsonDeserializer<T> {
        private final Coercing<?, ?> coercing;
        private final GraphQLContext graphQLContext;

        public CustomScalarDeserializer(Coercing<?, ?> coercing, GraphQLContext graphQLContext) {
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

    /** Helper class to wrap a scalar serialization into a Jackson JsonSerializer. */
    public static class CustomScalarSerializer<T> extends JsonSerializer<T> {
        private final Coercing<?, ?> coercing;
        private final GraphQLContext graphQLContext;

        public CustomScalarSerializer(Coercing<?, ?> coercing, GraphQLContext graphQLContext) {
            this.coercing = coercing;
            this.graphQLContext = graphQLContext;
        }

        @Override
        public void serialize(T value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            Object serializedValue = coercing.serialize(value, graphQLContext, Locale.getDefault());
            gen.writeString(serializedValue.toString());
        }
    }
}
