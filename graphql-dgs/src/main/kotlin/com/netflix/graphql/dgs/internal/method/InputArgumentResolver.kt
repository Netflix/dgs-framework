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

import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException
import com.netflix.graphql.dgs.internal.InputObjectMapper
import graphql.schema.DataFetchingEnvironment
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.KotlinDetector
import org.springframework.core.MethodParameter
import org.springframework.core.annotation.MergedAnnotation
import org.springframework.core.convert.TypeDescriptor
import org.springframework.core.convert.converter.ConditionalGenericConverter
import org.springframework.core.convert.converter.GenericConverter
import org.springframework.core.convert.support.DefaultConversionService
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Resolves method arguments annotated with [InputArgument].
 *
 * Argument conversion responsibilities are handled by the supplied [InputObjectMapper].
 */
class InputArgumentResolver(
    inputObjectMapper: InputObjectMapper
) : ArgumentResolver {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(InputArgumentResolver::class.java)
    }

    private val argumentNameCache: ConcurrentMap<MethodParameter, String> = ConcurrentHashMap()
    private val conversionService = DefaultConversionService()

    init {
        conversionService.addConverter(ObjectMapperConverter(inputObjectMapper))
    }

    private class ObjectMapperConverter(private val inputObjectMapper: InputObjectMapper) : ConditionalGenericConverter {
        override fun getConvertibleTypes(): Set<GenericConverter.ConvertiblePair> {
            return setOf(GenericConverter.ConvertiblePair(Map::class.java, Any::class.java))
        }

        override fun matches(sourceType: TypeDescriptor, targetType: TypeDescriptor): Boolean {
            return sourceType.isMap && !targetType.isMap
        }

        override fun convert(source: Any?, sourceType: TypeDescriptor, targetType: TypeDescriptor): Any {
            @Suppress("unchecked_cast")
            val mapInput = source as Map<String, *>
            return if (KotlinDetector.isKotlinType(targetType.type)) {
                inputObjectMapper.mapToKotlinObject(mapInput, targetType.type.kotlin)
            } else {
                inputObjectMapper.mapToJavaObject(mapInput, targetType.type)
            }
        }
    }

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(InputArgument::class.java)
    }

    override fun resolveArgument(parameter: MethodParameter, dfe: DataFetchingEnvironment): Any? {
        val argumentName = getArgumentName(parameter)
        val value: Any? = dfe.getArgument(argumentName)

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

    private fun getArgumentName(parameter: MethodParameter): String {
        val cachedName = argumentNameCache[parameter]
        if (cachedName != null) {
            return cachedName
        }

        val annotation = parameter.getParameterAnnotation(InputArgument::class.java)
            ?: throw IllegalArgumentException("Unsupported parameter type [${parameter.parameterType.name}]. supportsParameter should be called first.")

        val mergedAnnotation = MergedAnnotation.from(annotation).synthesize()

        val name = mergedAnnotation.name.ifBlank { parameter.parameterName }
            ?: throw IllegalArgumentException(
                "Name for argument of type [${parameter.nestedParameterType.name}}" +
                    " not specified, and parameter name information not found in class file either."
            )
        argumentNameCache[parameter] = name
        return name
    }

    private fun convertValue(source: Any?, target: TypeDescriptor): Any? {
        if (source == null) {
            return when (target.type) {
                Optional::class.java -> Optional.empty<Any?>()
                else -> null
            }
        }

        if (target.resolvableType.isInstance(source)) {
            return source
        }

        if (target.type == Optional::class.java) {
            val elementType = TypeDescriptor.valueOf(target.resolvableType.getGeneric(0).toClass())
            return Optional.ofNullable(convertValue(source, elementType))
        }

        val sourceType = TypeDescriptor.forObject(source)
        if (conversionService.canConvert(sourceType, target)) {
            return conversionService.convert(source, sourceType, target)
        }

        throw DgsInvalidInputArgumentException("Unable to convert from ${source.javaClass} to ${target.type}")
    }
}
