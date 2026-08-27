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

package com.netflix.graphql.dgs.internal;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.Jackson3JsonProvider;
import com.jayway.jsonpath.spi.mapper.Jackson3MappingProvider;
import com.netflix.graphql.dgs.json.DgsJsonMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.kotlin.KotlinModule;

/**
 * Jackson 3 implementation of {@link DgsJsonMapper}.
 * This is the default implementation used when Jackson 3 is on the classpath.
 * Note: Jackson 3 has built-in Java time support, so no separate JavaTimeModule is needed.
 */
public class Jackson3DgsJsonMapper implements DgsJsonMapper {
    private final JsonMapper mapper = JsonMapper
            .builder()
            .addModule(new KotlinModule.Builder().build())
            .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    @Override
    public String writeValueAsString(Object value) {
        return mapper.writeValueAsString(value);
    }

    @Override
    public <T> T readValue(String content, Class<T> clazz) {
        return mapper.readValue(content, clazz);
    }

    @Override
    public <T> T convertValue(Object fromValue, Class<T> toClass) {
        return mapper.convertValue(fromValue, toClass);
    }

    @Override
    public Configuration jsonPathConfiguration() {
        return Configuration
                .builder()
                .jsonProvider(new Jackson3JsonProvider(mapper))
                .mappingProvider(new Jackson3MappingProvider(mapper))
                .build()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);
    }
}
