/*
 * Copyright 2021 Netflix, Inc.
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

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

object InputObjectMapper {
    fun <T : Any> mapToKotlinObject(inputMap: Map<String, *>, targetClass: KClass<T>): T {
        val params = targetClass.primaryConstructor!!.parameters
        val inputValues = mutableListOf<Any?>()

        params.forEach { parameter ->
            val input = inputMap[parameter.name]
            if (input is Map<*, *>) {
                val nestedTarget = parameter.type.jvmErasure
                val subValue = if (nestedTarget.java == Object::class.java || nestedTarget == Any::class) {
                    input
                } else if (nestedTarget.java.isKotlinClass()) {
                    mapToKotlinObject(input as Map<String, *>, nestedTarget)
                } else {
                    mapToJavaObject(input as Map<String, *>, nestedTarget.java)
                }
                inputValues.add(subValue)
            } else if (parameter.type.jvmErasure.java.isEnum && input !== null) {
                val enumValue = (parameter.type.jvmErasure.java.enumConstants as Array<Enum<*>>).find { enumValue -> enumValue.name == input }
                inputValues.add(enumValue)
            } else if (input is List<*>) {
                val newList = convertList(input, parameter.type.arguments[0].type!!.jvmErasure)
                inputValues.add(newList)
            } else {
                inputValues.add(input)
            }
        }

        return targetClass.primaryConstructor!!.call(*inputValues.toTypedArray())
    }

    private fun convertList(input: List<*>, nestedTarget: KClass<*>): List<*> {
        return input.map { listItem ->
            if (listItem is Map<*, *>) {
                if (nestedTarget.java == Object::class.java || nestedTarget == Any::class) {
                    listItem
                } else if (nestedTarget.java.isKotlinClass()) {
                    mapToKotlinObject(listItem as Map<String, *>, nestedTarget)
                } else {
                    mapToJavaObject(listItem as Map<String, *>, nestedTarget.java)
                }
            } else {
                listItem
            }
        }
    }

    fun <T> mapToJavaObject(inputMap: Map<String, *>, targetClass: Class<T>): T {
        if (targetClass == Object::class.java) {
            return inputMap as T
        }

        val ctor = targetClass.getDeclaredConstructor()
        ctor.isAccessible = true
        val instance = ctor.newInstance()
        inputMap.forEach {
            val declaredField = targetClass.getDeclaredField(it.key)
            declaredField.isAccessible = true

            if (it.value is Map<*, *>) {
                val mappedValue = if (declaredField.type.isKotlinClass()) {
                    mapToKotlinObject(it.value as Map<String, *>, declaredField.type.kotlin)
                } else {
                    mapToJavaObject(it.value as Map<String, *>, declaredField.type)
                }

                declaredField.set(instance, mappedValue)
            } else if (declaredField.type.isEnum) {
                val enumValue = (declaredField.type.enumConstants as Array<Enum<*>>).find { enumValue -> enumValue.name == it.value }
                declaredField.set(instance, enumValue)
            } else if (it.value is List<*>) {
                val actualType: Type = (declaredField.genericType as ParameterizedType).actualTypeArguments[0]
                val newList = convertList(it.value as List<*>, Class.forName(actualType.typeName).kotlin)
                declaredField.set(instance, newList)
            } else {
                declaredField.set(instance, it.value)
            }
        }

        return instance
    }
}
