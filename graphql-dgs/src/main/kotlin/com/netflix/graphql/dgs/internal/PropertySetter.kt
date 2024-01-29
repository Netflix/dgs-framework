/*
 * Copyright 2024 Netflix, Inc.
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
import org.springframework.beans.PropertyAccessor
import org.springframework.beans.PropertyAccessorFactory
import org.springframework.core.convert.ConversionException
import org.springframework.core.convert.support.DefaultConversionService

/**
 * <code>PropertySetter</code> allows for calling setter methods when they exist and falls back to direct field access
 * when they don't.
 * <p>
 * Type conversion is done explicitly as there are some cases that Spring <code>PropertyAccessor</code>s don't handle
 * out of the box.
 */
class PropertySetter(target: Any, private val conversionService: DefaultConversionService) {

    private val propertyAccessor = PropertyAccessorFactory.forBeanPropertyAccess(target)
    private val fieldAccessor = PropertyAccessorFactory.forDirectFieldAccess(target)

    fun hasProperty(propertyName: String): Boolean {
        return propertyAccessor.isWritableProperty(propertyName) || fieldAccessor.isWritableProperty(propertyName)
    }

    fun trySet(propertyName: String, value: Any?) {
        if (propertyAccessor.isWritableProperty(propertyName)) {
            val convertedValue = convertValue(propertyName, value, propertyAccessor)

            propertyAccessor.setPropertyValue(propertyName, convertedValue)
        } else {
            val convertedValue = convertValue(propertyName, value, fieldAccessor)

            fieldAccessor.setPropertyValue(propertyName, convertedValue)
        }
    }

    private fun convertValue(propertyName: String, value: Any?, accessor: PropertyAccessor): Any? {
        val fieldType = accessor.getPropertyTypeDescriptor(propertyName)!!

        return try {
            conversionService.convert(value, fieldType)
        } catch (exc: ConversionException) {
            throw DgsInvalidInputArgumentException("Failed to convert value $value to $fieldType", exc)
        }
    }
}
