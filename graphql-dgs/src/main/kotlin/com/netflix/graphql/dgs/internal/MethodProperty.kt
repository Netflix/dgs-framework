/*
 * Copyright 2023 Netflix, Inc.
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

import org.springframework.util.ReflectionUtils
import java.lang.reflect.Method
import java.lang.reflect.Type

class MethodProperty(name: String, private val method: Method, targetClass: Class<*>) : BaseProperty(name, targetClass) {

    override fun getPropertyType(): Type {
        val genericTypes = method.genericParameterTypes
        val parameterType = parameterTypeOf()

        return if (genericTypes.size == 1) determineType(genericTypes[0], parameterType) else parameterType
    }

    override fun getRawPropertyType(): Class<*> {
        return parameterTypeOf()
    }

    override fun trySet(instance: Any, value: Any?) {
        assertValueAssignable(value)
        ReflectionUtils.makeAccessible(method)
        method.invoke(instance, value)
    }

    private fun parameterTypeOf(): Class<*> {
        val types = method.parameterTypes

        if (types.size == 1) {
            return types[0]
        }

        return Void::class.java
    }
}