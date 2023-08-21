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
import java.lang.reflect.Field
import java.lang.reflect.Type

class FieldProperty(name: String, private val field: Field, targetClass: Class<*>) : BaseProperty(name, targetClass) {

    override fun getPropertyType(): Type {
        return determineType(field.genericType, field.type)
    }

    override fun getRawPropertyType(): Class<*> {
        return field.type
    }

    override fun trySet(instance: Any, value: Any?) {
        assertValueAssignable(value)
        ReflectionUtils.makeAccessible(field)
        ReflectionUtils.setField(field, instance, value)
    }
}