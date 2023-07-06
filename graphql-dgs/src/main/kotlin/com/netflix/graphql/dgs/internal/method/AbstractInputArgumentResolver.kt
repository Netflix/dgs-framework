/*
 * Copyright 2022 Netflix, Inc.
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

import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException
import com.netflix.graphql.dgs.internal.InputObjectMapper
import graphql.schema.DataFetchingEnvironment
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.core.convert.TypeDescriptor
import org.springframework.core.convert.support.DefaultConversionService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

abstract class AbstractInputArgumentResolver(inputObjectMapper: InputObjectMapper) : ArgumentResolver {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AbstractInputArgumentResolver::class.java)
    }

    private val conversionService = DefaultConversionService()
    private val argumentNameCache: ConcurrentMap<MethodParameter, String> = ConcurrentHashMap()

    init {
        conversionService.addConverter(InputObjectMapperConverter(inputObjectMapper))
    }

    override fun resolveArgument(parameter: MethodParameter, dfe: DataFetchingEnvironment): Any? {
        val argumentName = getArgumentName(parameter)
        val value = dfe.getArgument<Any?>(argumentName)

        val typeDescriptor = TypeDescriptor(parameter)
        val convertedValue = convertValue(value, typeDescriptor)

        if (convertedValue == null && dfe.fieldDefinition.arguments.none { it.name == argumentName }) {
            logger.warn(
                "Unknown argument '{}'",
                argumentName
            )
        }

        return convertedValue
    }

    internal abstract fun resolveArgumentName(parameter: MethodParameter): String?

    private fun getArgumentName(parameter: MethodParameter): String? {
        val cachedName = argumentNameCache[parameter]
        if (cachedName != null) {
            return cachedName
        }
        val name = resolveArgumentName(parameter)
        argumentNameCache[parameter] = name
        return name
    }

    private fun convertValue(source: Any?, target: TypeDescriptor): Any? {
        if (target.resolvableType.isInstance(source)) {
            return source
        }

        val sourceType = TypeDescriptor.forObject(source)
        if (conversionService.canConvert(sourceType, target)) {
            return conversionService.convert(source, sourceType, target)
        }

        throw DgsInvalidInputArgumentException("Unable to convert from ${source?.javaClass} to ${target.type}")
    }
}
