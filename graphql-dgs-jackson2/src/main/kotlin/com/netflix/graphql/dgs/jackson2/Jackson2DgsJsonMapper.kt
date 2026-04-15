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

package com.netflix.graphql.dgs.jackson2

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import com.netflix.graphql.dgs.internal.DgsJsonMapper

/**
 * Jackson 2 implementation of [DgsJsonMapper].
 * Used when consumers opt back into Jackson 2 via the `graphql-dgs-jackson2` module`.
 */
class Jackson2DgsJsonMapper : DgsJsonMapper {
    val objectMapper: ObjectMapper =
        ObjectMapper()
            .registerModule(kotlinModule { enable(KotlinFeature.NullIsSameAsDefault) })
            .registerModule(JavaTimeModule())
            .registerModule(ParameterNamesModule())
            .registerModule(Jdk8Module())
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

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
}
