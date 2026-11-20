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

package com.netflix.graphql.dgs.internal.method

import com.netflix.graphql.dgs.Source
import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper
import com.netflix.graphql.dgs.internal.InputObjectMapper
import graphql.schema.DataFetchingEnvironment
import org.springframework.core.MethodParameter
import org.springframework.core.convert.TypeDescriptor

/**
 * Resolves a parameter annotated with [Source] to the source object of the field.
 *
 * Use it for a data fetcher on a non-root field that needs the parent object.
 *
 * - A source that is an instance of the parameter type is passed as is.
 * - A source that is a [Map] is converted to the parameter type with [InputObjectMapper].
 *   A parent data fetcher can return a [Map] for an object type.
 * - Any other source fails with an [IllegalArgumentException].
 * - A `null` source fails, because graphql-java gives a `null` source only to a root field.
 */
class SourceArgumentResolver(
    inputObjectMapper: InputObjectMapper = DefaultInputObjectMapper(),
) : ArgumentResolver {
    private val mapConverter = InputObjectMapperConverter(inputObjectMapper)

    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.hasParameterAnnotation(Source::class.java)

    override fun resolveArgument(
        parameter: MethodParameter,
        dfe: DataFetchingEnvironment,
    ): Any {
        val source =
            dfe.getSource<Any>()
                ?: throw IllegalArgumentException("Source is null. Are you trying to use @Source on a root field (e.g. @DgsQuery)?")

        if (parameter.parameterType.isAssignableFrom(source.javaClass)) {
            return source
        }

        val sourceType = TypeDescriptor.valueOf(source.javaClass)
        val targetType = TypeDescriptor(parameter)
        if (mapConverter.matches(sourceType, targetType)) {
            return mapConverter.convert(source, sourceType, targetType)
        }

        throw IllegalArgumentException(
            "Invalid source type '${source.javaClass.name}'. Expected type '${parameter.parameterType.name}'",
        )
    }
}
