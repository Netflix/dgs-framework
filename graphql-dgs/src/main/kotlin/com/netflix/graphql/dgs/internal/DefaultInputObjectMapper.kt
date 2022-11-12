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
import org.springframework.util.CollectionUtils
import org.springframework.util.ReflectionUtils
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

@Suppress("UNCHECKED_CAST")
class DefaultInputObjectMapper(private val customInputObjectMapper: InputObjectMapper? = null) : InputObjectMapper {
    private val logger: Logger = LoggerFactory.getLogger(InputObjectMapper::class.java)

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

            if (input is Map<*, *>) {
                val nestedTarget = parameter.type.jvmErasure
                val subValue = if (isObjectOrAny(nestedTarget)) {
                    input
                } else if (KotlinDetector.isKotlinType(nestedTarget.java)) {
                    customInputObjectMapper?.mapToKotlinObject(input as Map<String, *>, nestedTarget)
                        ?: mapToKotlinObject(input as Map<String, *>, nestedTarget)
                } else {
                    customInputObjectMapper?.mapToJavaObject(input as Map<String, *>, nestedTarget.java)
                        ?: mapToJavaObject(input as Map<String, *>, nestedTarget.java)
                }
                parametersByName[parameter] = subValue
            } else if (parameter.type.jvmErasure.java.isEnum && input !== null) {
                val enumValue =
                    (parameter.type.jvmErasure.java.enumConstants as Array<Enum<*>>).find { enumValue -> enumValue.name == input }
                parametersByName[parameter] = enumValue
            } else if (input is List<*>) {
                val newList = convertList(
                    input = input,
                    targetClass = targetClass.java,
                    nestedClass = parameter.type.arguments[0].type!!.jvmErasure,
                    nestedType =
                    if (parameter.type.arguments[0].type!!.arguments.isNotEmpty()) {
                        ((parameter.type.arguments[0].type!!.arguments[0].type) as KType).javaType
                    } else {
                        null
                    }
                )

                if (parameter.type.jvmErasure == Set::class) {
                    parametersByName[parameter] = newList.toSet()
                } else {
                    parametersByName[parameter] = newList
                }
            } else {
                parametersByName[parameter] = input
            }
        }

        return try {
            constructor.callBy(parametersByName)
        } catch (ex: Exception) {
            throw DgsInvalidInputArgumentException("Provided input arguments do not match arguments of data class `$targetClass`", ex)
        }
    }

    override fun <T> mapToJavaObject(inputMap: Map<String, *>, targetClass: Class<T>): T {
        if (targetClass == Object::class.java || targetClass == Map::class.java) {
            return inputMap as T
        }

        val ctor = ReflectionUtils.accessibleConstructor(targetClass)
        ReflectionUtils.makeAccessible(ctor)
        val instance = ctor.newInstance()
        var nrOfFieldErrors = 0
        inputMap.forEach {
            val declaredField = ReflectionUtils.findField(targetClass, it.key)
            if (declaredField != null) {
                val fieldType = getFieldType(declaredField, targetClass)
                // resolve the field class we will map into, as well as an optional type argument in case such
                // class is a parameterized type, such as a List.
                val (fieldClass: Class<*>, fieldArgumentType: Type?) = when (fieldType) {
                    is ParameterizedType -> fieldType.rawType as Class<*> to fieldType.actualTypeArguments[0]
                    is Class<*> -> fieldType to null
                    else -> Class.forName(fieldType.typeName) to null
                }

                if (it.value is Map<*, *>) {
                    val mappedValue = if (KotlinDetector.isKotlinType(fieldClass)) {
                        mapToKotlinObject(it.value as Map<String, *>, fieldClass.kotlin)
                    } else {
                        mapToJavaObject(it.value as Map<String, *>, fieldClass)
                    }
                    trySetField(declaredField, instance, mappedValue)
                } else if (it.value is List<*>) {
                    val newList = convertList(it.value as List<*>, targetClass, fieldClass.kotlin, fieldArgumentType)
                    if (declaredField.type == Set::class.java) {
                        trySetField(declaredField, instance, newList.toSet())
                    } else {
                        trySetField(declaredField, instance, newList)
                    }
                } else if (fieldClass.isEnum) {
                    val enumValue =
                        (fieldClass.enumConstants as Array<Enum<*>>).find { enumValue -> enumValue.name == it.value }
                    trySetField(declaredField, instance, enumValue)
                } else {
                    trySetField(declaredField, instance, it.value)
                }
            } else {
                logger.warn("Field '${it.key}' was not found on Input object of type '$targetClass'")
                nrOfFieldErrors++
            }
        }

        /**
         We can't error out if only some fields don't match.
         This would happen if new schema fields are added, but the Java type wasn't updated yet.
         If none of the fields match however, it's a pretty good indication that the wrong type was used, hence this check.
         */
        if (inputMap.isNotEmpty() && nrOfFieldErrors == inputMap.size) {
            throw DgsInvalidInputArgumentException("Input argument type '$targetClass' doesn't match input $inputMap")
        }

        return instance
    }

    private fun trySetField(declaredField: Field, instance: Any?, value: Any?) {
        try {
            declaredField.isAccessible = true
            declaredField.set(instance, value)
        } catch (ex: Exception) {
            throw DgsInvalidInputArgumentException("Invalid input argument `$value` for field `${declaredField.name}` on type `${instance?.javaClass?.name}`")
        }
    }

    private fun getFieldType(field: Field, targetClass: Class<*>): Type {
        val genericSuperclass = targetClass.genericSuperclass
        val fieldType: Type = field.genericType
        return if (fieldType is ParameterizedType && fieldType.actualTypeArguments.size == 1) {
            fieldType.actualTypeArguments[0]
        } else if (genericSuperclass is ParameterizedType && field.type != field.genericType) {
            val typeParameters = (genericSuperclass.rawType as Class<*>).typeParameters
            val indexOfTypeParameter = typeParameters.indexOfFirst { it.name == fieldType.typeName }
            genericSuperclass.actualTypeArguments[indexOfTypeParameter]
        } else {
            field.type
        }
    }

    private fun convertList(
        input: List<*>,
        targetClass: Class<*>,
        nestedClass: KClass<*>,
        nestedType: Type? = null
    ): List<*> {
        val mappedList = input.filterNotNull().map { listItem ->
            if (listItem is List<*>) {
                when (nestedType) {
                    is ParameterizedType ->
                        convertList(
                            listItem,
                            targetClass,
                            (nestedType.rawType as Class<*>).kotlin,
                            nestedType.actualTypeArguments[0]
                        )
                    is TypeVariable<*> -> {
                        val indexOfGeneric =
                            ((targetClass.genericSuperclass as ParameterizedType).rawType as Class<*>)
                                .typeParameters.indexOfFirst { it.name == nestedType.typeName }
                        val parameterType =
                            (targetClass.genericSuperclass as ParameterizedType).actualTypeArguments[indexOfGeneric]
                        convertList(listItem, targetClass, (parameterType as Class<*>).kotlin)
                    }
                    is WildcardType -> {
                        // We are assuming that the upper-bound type is a Class and not a Parametrized Type.
                        convertList(listItem, targetClass, (nestedType.upperBounds[0] as Class<*>).kotlin)
                    }
                    is Class<*> ->
                        convertList(listItem, targetClass, nestedType.kotlin)
                    else ->
                        listItem
                }
            } else if (nestedClass.java.isEnum) {
                (nestedClass.java.enumConstants as Array<Enum<*>>).first { it.name == listItem }
            } else if (listItem is Map<*, *>) {
                if (isObjectOrAny(nestedClass)) {
                    listItem
                } else if (KotlinDetector.isKotlinType(nestedClass.java)) {
                    mapToKotlinObject(listItem as Map<String, *>, nestedClass)
                } else {
                    mapToJavaObject(listItem as Map<String, *>, nestedClass.java)
                }
            } else {
                listItem
            }
        }

        return mappedList
    }

    private fun isObjectOrAny(nestedTarget: KClass<*>) =
        nestedTarget.java == Object::class.java || nestedTarget == Any::class
}
