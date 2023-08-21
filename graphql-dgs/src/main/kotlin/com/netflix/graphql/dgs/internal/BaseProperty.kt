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

import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

abstract class BaseProperty(private val name: String, private val targetClass: Class<*>) : Property {

    private val genericSuperclass: Type? = targetClass.genericSuperclass

    protected fun determineType(genericType: Type, declaredType: Class<*>): Type {
        if (genericType is ParameterizedType && genericType.actualTypeArguments.size == 1) {
            return genericType.actualTypeArguments[0]
        }

        if (genericSuperclass is ParameterizedType && genericType != declaredType) {
            val typeParameters = (genericSuperclass.rawType as Class<*>).typeParameters
            val indexOfTypeParameter = typeParameters.indexOfFirst { it.name == genericType.typeName }

            return genericSuperclass.actualTypeArguments[indexOfTypeParameter]
        }

        return declaredType
    }

    protected fun assertValueAssignable(value: Any?) {
        if (value != null && !getRawPropertyType().isAssignableFrom(value.javaClass)) {
            throw DgsInvalidInputArgumentException("Invalid input argument `$value` for property `$name` on type `${targetClass.name}`")
        }
    }
}