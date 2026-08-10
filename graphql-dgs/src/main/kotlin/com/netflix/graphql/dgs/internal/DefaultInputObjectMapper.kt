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

package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.ConfigurablePropertyAccessor
import org.springframework.beans.PropertyAccessorFactory
import org.springframework.core.KotlinDetector
import org.springframework.core.ResolvableType
import org.springframework.core.convert.ConversionException
import org.springframework.core.convert.TypeDescriptor
import org.springframework.core.convert.converter.ConditionalGenericConverter
import org.springframework.core.convert.converter.GenericConverter
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.util.CollectionUtils
import java.lang.reflect.Constructor
import java.lang.reflect.Type
import java.util.Optional
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

class DefaultInputObjectMapper(
    customInputObjectMapper: InputObjectMapper? = null,
) : InputObjectMapper {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(InputObjectMapper::class.java)
    }

    private val conversionService = DefaultConversionService()

    init {
        conversionService.addConverter(Converter(customInputObjectMapper ?: this))
    }

    private class Converter(
        private val mapper: InputObjectMapper,
    ) : ConditionalGenericConverter {
        override fun getConvertibleTypes(): Set<GenericConverter.ConvertiblePair> =
            setOf(GenericConverter.ConvertiblePair(Map::class.java, Any::class.java))

        override fun matches(
            sourceType: TypeDescriptor,
            targetType: TypeDescriptor,
        ): Boolean {
            if (targetType.type == Optional::class.java) {
                // Let Spring's ObjectToOptionalConverter handle it
                return false
            }
            if (sourceType.isMap) {
                val keyDescriptor = sourceType.mapKeyTypeDescriptor
                return keyDescriptor == null || keyDescriptor.type == String::class.java
            }
            return false
        }

        override fun convert(
            source: Any?,
            sourceType: TypeDescriptor,
            targetType: TypeDescriptor,
        ): Any? {
            @Suppress("UNCHECKED_CAST")
            val sourceMap = source as Map<String, *>
            if (KotlinDetector.isKotlinType(targetType.type)) {
                return mapper.mapToKotlinObject(sourceMap, targetType.type.kotlin)
            }
            return mapper.mapToJavaObject(sourceMap, targetType.type)
        }
    }

    override fun <T : Any> mapToKotlinObject(
        inputMap: Map<String, *>,
        targetClass: KClass<T>,
    ): T {
        val constructor =
            targetClass.primaryConstructor
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
            parametersByName[parameter] = maybeConvert(input, parameter.type)
        }

        return try {
            constructor.callBy(parametersByName)
        } catch (ex: Exception) {
            throw DgsInvalidInputArgumentException("Provided input arguments do not match arguments of data class `$targetClass`", ex)
        }
    }

    private fun maybeConvert(
        input: Any?,
        parameterType: KType,
    ): Any? {
        // Check if input is already an instance of the parameter type; we check against the KType / KClass
        // to support inline value classes.
        if (parameterType.arguments.isEmpty() && parameterType.jvmErasure.isInstance(input)) {
            return input
        }
        return maybeConvert(input, parameterType.javaType)
    }

    private fun maybeConvert(
        input: Any?,
        parameterType: Type,
    ): Any? {
        val targetType: TypeDescriptor
        if (parameterType is Class<*>) {
            if (parameterType.isInstance(input)) {
                // No conversion necessary
                return input
            }
            targetType = TypeDescriptor.valueOf(parameterType)
        } else {
            targetType = TypeDescriptor(ResolvableType.forType(parameterType), null, null)
        }
        val sourceType = TypeDescriptor.forObject(input)

        try {
            return conversionService.convert(input, sourceType, targetType)
        } catch (exc: ConversionException) {
            throw DgsInvalidInputArgumentException("Failed to convert value $input to $targetType", exc)
        }
    }

    override fun <T> mapToJavaObject(
        inputMap: Map<String, *>,
        targetClass: Class<T>,
    ): T {
        if (targetClass.isAssignableFrom(inputMap::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return inputMap as T
        }

        if (targetClass.isRecord) {
            return handleRecordClass(inputMap, targetClass)
        }

        @Suppress("UNCHECKED_CAST")
        val ctor = targetClass.getDeclaredConstructor() as Constructor<T>
        ctor.trySetAccessible()
        val instance = ctor.newInstance()
        val setterAccessor = setterAccessor(instance)
        val fieldAccessor = fieldAccessor(instance)
        var nrOfPropertyErrors = 0

        for ((name, value) in inputMap.entries) {
            try {
                if (setterAccessor.isWritableProperty(name)) {
                    setterAccessor.setPropertyValue(name, value)
                } else if (fieldAccessor.isWritableProperty(name)) {
                    fieldAccessor.setPropertyValue(name, value)
                } else {
                    nrOfPropertyErrors++
                    logger.warn("Field or property '{}' was not found on Input object of type '{}'", name, targetClass)
                }
            } catch (ex: Exception) {
                throw DgsInvalidInputArgumentException(
                    "Invalid input argument `$value` for field/property `$name` on type `${targetClass.name}`",
                    ex,
                )
            }
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

    private fun <T> handleRecordClass(
        inputMap: Map<String, Any?>,
        targetClass: Class<T>,
    ): T {
        val recordComponents = targetClass.recordComponents
        val args = arrayOfNulls<Any?>(recordComponents.size)
        for ((index, component) in recordComponents.withIndex()) {
            if (component.name in inputMap) {
                args[index] = maybeConvert(inputMap[component.name], component.genericType)
            }
        }
        @Suppress("UNCHECKED_CAST")
        val ctor = targetClass.declaredConstructors.first() as Constructor<T>
        ctor.trySetAccessible()
        try {
            return ctor.newInstance(*args)
        } catch (exc: ReflectiveOperationException) {
            throw DgsInvalidInputArgumentException("Failed to construct record, class=${targetClass.simpleName}", exc)
        }
    }

    private fun <T> fieldAccessor(instance: T?): ConfigurablePropertyAccessor {
        val accessor = PropertyAccessorFactory.forDirectFieldAccess(instance as Any)

        accessor.conversionService = conversionService
        return accessor
    }

    private fun <T> setterAccessor(instance: T?): ConfigurablePropertyAccessor {
        val accessor = PropertyAccessorFactory.forBeanPropertyAccess(instance as Any)

        accessor.conversionService = conversionService
        return accessor
    }
}
