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

package com.netflix.graphql.dgs.internal

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.Jackson3JsonProvider
import com.jayway.jsonpath.spi.mapper.Jackson3MappingProvider
import com.netflix.graphql.dgs.json.DgsJsonMapper
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * Jackson 3 implementation of [DgsJsonMapper].
 * This is the default implementation used when Jackson 3 is on the classpath.
 * Note: Jackson 3 has built-in Java time support, so no separate JavaTimeModule is needed.
 */
class Jackson3DgsJsonMapper : DgsJsonMapper {
    private val mapper: JsonMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build()

    override fun writeValueAsString(value: Any): String = mapper.writeValueAsString(value)

    override fun <T> readValue(
        content: String,
        clazz: Class<T>,
    ): T = mapper.readValue(content, clazz)

    override fun <T> convertValue(
        fromValue: Any,
        toClass: Class<T>,
    ): T = mapper.convertValue(fromValue, toClass)

    override fun jsonPathConfiguration(): Configuration =
        Configuration
            .builder()
            .jsonProvider(Jackson3JsonProvider(mapper))
            .mappingProvider(Jackson3MappingProvider(mapper))
            .build()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)
}
