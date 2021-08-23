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
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

object InputObjectMapper {
    fun <T : Any> mapToKotlinObject(inputMap: Map<String, *>, targetClass: KClass<T>): T {
        val params = targetClass.primaryConstructor!!.parameters
        val inputValues = mutableListOf<Any?>()

        params.forEach {
            val input = inputMap[it.name]
            if (input is Map<*, *>) {
                val nestedTarget = it.type.jvmErasure
                val subValue = if (nestedTarget.java.isKotlinClass()) {
                    mapToKotlinObject(input as Map<String, *>, nestedTarget)
                } else {
                    mapToJavaObject(input as Map<String, *>, nestedTarget.java)
                }
                inputValues.add(subValue)
            } else if (it.type.jvmErasure.java.isEnum) {
                val enumValue = (it.type.jvmErasure.java.enumConstants as Array<Enum<*>>).find { enumValue -> enumValue.name == input }
                inputValues.add(enumValue)
            } else {
                inputValues.add(input)
            }
        }

        return targetClass.primaryConstructor!!.call(*inputValues.toTypedArray())
    }

    fun <T> mapToJavaObject(inputMap: Map<String, *>, targetClass: Class<T>): T {
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
            } else {
                declaredField.set(instance, it.value)
            }
        }

        return instance
    }
}
