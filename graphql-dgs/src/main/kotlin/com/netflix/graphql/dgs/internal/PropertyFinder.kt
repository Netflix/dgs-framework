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
import java.lang.reflect.Method

class PropertyFinder {

    companion object {
        fun findProperty(propertyName: String, targetClass: Class<*>): Property? {
            val method = methodFor(propertyName, targetClass)

            if (method != null) {
                return MethodProperty(propertyName, method, targetClass)
            }

            val field = fieldFor(propertyName, targetClass)

            return if (field != null) FieldProperty(propertyName, field, targetClass) else null
        }

        private fun methodFor(propertyName: String, targetClass: Class<*>): Method? {
            val methodName = toMethodName(propertyName)
            val methods = ReflectionUtils.getAllDeclaredMethods(targetClass)

            // We need to get the Method instance from the class it is actually declared in (base class compared to subclass),
            // in order to have all the necessary parameter type information.
            return methods.findLast { m -> methodName == m.name }
        }

        private fun toMethodName(propertyName: String): String {
            if (propertyName.isEmpty()) {
                return propertyName
            }

            return "set" + propertyName.substring(0, 1).uppercase() + propertyName.substring(1)
        }

        private fun fieldFor(propertyName: String, targetClass: Class<*>): Field? {
            return ReflectionUtils.findField(targetClass, propertyName)
        }
    }
}