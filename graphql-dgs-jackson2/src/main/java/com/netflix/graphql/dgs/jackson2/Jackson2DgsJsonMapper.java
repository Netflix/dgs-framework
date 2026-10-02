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

package com.netflix.graphql.dgs.jackson2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.kotlin.KotlinFeature;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.netflix.graphql.dgs.json.DgsJsonMapper;
import org.jetbrains.annotations.NotNull;


/**
 * Jackson 2 implementation of {@link DgsJsonMapper}.
 * Used when consumers opt back into Jackson 2 via the {@code graphql-dgs-jackson2} module.
 */
class Jackson2DgsJsonMapper implements DgsJsonMapper {
    private final ObjectMapper objectMapper = Jackson2DgsDefaultsSupport.addTo(
            new ObjectMapper()
                    .registerModule(new KotlinModule.Builder().enable(KotlinFeature.NullIsSameAsDefault).build())
                    .registerModule(new JavaTimeModule())
                    .registerModule(new ParameterNamesModule())
                    .registerModule(new Jdk8Module())
                    .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES));

    @NotNull
    @Override
    public String writeValueAsString(@NotNull Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw rethrow(e);
        }
    }

    @Override
    public <T> T readValue(@NotNull String content, @NotNull Class<T> clazz) {
        try {
            return objectMapper.readValue(content, clazz);
        } catch (JsonProcessingException e) {
            throw rethrow(e);
        }
    }

    @Override
    public <T> T convertValue(@NotNull Object fromValue, @NotNull Class<T> toClass) {
        return objectMapper.convertValue(fromValue, toClass);
    }

    @NotNull
    @Override
    public Configuration jsonPathConfiguration() {
        return Configuration.builder()
                .jsonProvider(new JacksonJsonProvider(objectMapper))
                .mappingProvider(new JacksonMappingProvider(objectMapper))
                .build()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);
    }

    private static RuntimeException rethrow(JsonProcessingException exception) {
        Jackson2DgsJsonMapper.<RuntimeException>sneakyThrow(exception);
        throw new AssertionError("unreachable");
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable throwable) throws E {
        throw (E) throwable;
    }
}
