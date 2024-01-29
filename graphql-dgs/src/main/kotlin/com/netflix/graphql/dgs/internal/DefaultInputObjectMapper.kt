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

package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.KotlinDetector
import org.springframework.core.ResolvableType
import org.springframework.core.convert.ConversionException
import org.springframework.core.convert.TypeDescriptor
import org.springframework.core.convert.converter.ConditionalGenericConverter
import org.springframework.core.convert.converter.GenericConverter
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.util.CollectionUtils
import org.springframework.util.ReflectionUtils
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

class DefaultInputObjectMapper(customInputObjectMapper: InputObjectMapper? = null) : InputObjectMapper {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(InputObjectMapper::class.java)
    }

    private val conversionService = DefaultConversionService()

    init {
        conversionService.addConverter(Converter(customInputObjectMapper ?: this))
    }

    private class Converter(private val mapper: InputObjectMapper) : ConditionalGenericConverter {
        override fun getConvertibleTypes(): Set<GenericConverter.ConvertiblePair> {
            return setOf(GenericConverter.ConvertiblePair(Map::class.java, Any::class.java))
        }

        override fun matches(sourceType: TypeDescriptor, targetType: TypeDescriptor): Boolean {
            if (sourceType.isMap) {
                val keyDescriptor = sourceType.mapKeyTypeDescriptor
                return keyDescriptor == null || keyDescriptor.type == String::class.java
            }
            return false
        }

        override fun convert(source: Any?, sourceType: TypeDescriptor, targetType: TypeDescriptor): Any? {
            @Suppress("UNCHECKED_CAST")
            val sourceMap = source as Map<String, *>
            if (KotlinDetector.isKotlinType(targetType.type)) {
                return mapper.mapToKotlinObject(sourceMap, targetType.type.kotlin)
            }
            return mapper.mapToJavaObject(sourceMap, targetType.type)
        }
    }

    override fun <T : Any> mapToKotlinObject(inputMap: Map<String, *>, targetClass: KClass<T>): T {
        val constructor = targetClass.primaryConstructor
            ?: throw DgsInvalidInputArgumentException("No primary constructor found for class $targetClass")

        val parameters = constructor.parameters
        val parametersByName = CollectionUtils.newLinkedHashMap<KParameter, Any?>(parameters.size)

        for (parameter in parameters) {
            if (parameter.name !in inputMap) {
                if (parameter.isOptional) {
                    continue
                } else if (parameter.type.isMarkedNullable) {
                    parametersByName[parameter] = null
                    continue
                }
                throw DgsInvalidInputArgumentException("No value specified for required parameter ${parameter.name} of class $targetClass")
            }

            val input = inputMap[parameter.name]
            val typeDescriptor = TypeDescriptor(ResolvableType.forType(parameter.type.javaType), null, null)
            val convertedValue = try {
                conversionService.convert(input, typeDescriptor)
            } catch (exc: ConversionException) {
                throw throw DgsInvalidInputArgumentException("Failed to convert value $input to $typeDescriptor", exc)
            }
            parametersByName[parameter] = convertedValue
        }

        return try {
            constructor.callBy(parametersByName)
        } catch (ex: Exception) {
            throw DgsInvalidInputArgumentException("Provided input arguments do not match arguments of data class `$targetClass`", ex)
        }
    }

    override fun <T> mapToJavaObject(inputMap: Map<String, *>, targetClass: Class<T>): T {
        if (targetClass.isAssignableFrom(inputMap::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return inputMap as T
        }

        val ctor = ReflectionUtils.accessibleConstructor(targetClass)
        val instance = ctor.newInstance()
        val setter = PropertySetter(instance as Any, conversionService)
        var nrOfPropertyErrors = 0
        for ((name, value) in inputMap.entries) {
            if (!setter.hasProperty(name)) {
                nrOfPropertyErrors++
                logger.warn("Field '{}' was not found on Input object of type '{}'", name, targetClass)
                continue
            }

            setter.trySet(name, value)
        }

        /**
         We can't error out if only some fields don't match.
         This would happen if new schema fields are added, but the Java type wasn't updated yet.
         If none of the fields match however, it's a pretty good indication that the wrong type was used, hence this check.
         */
        if (inputMap.isNotEmpty() && nrOfPropertyErrors == inputMap.size) {
            throw DgsInvalidInputArgumentException("Input argument type '$targetClass' doesn't match input $inputMap")
        }

        return instance
    }
}
