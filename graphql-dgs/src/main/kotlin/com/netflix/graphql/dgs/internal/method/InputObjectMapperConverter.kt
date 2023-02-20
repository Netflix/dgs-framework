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

package com.netflix.graphql.dgs.internal.method

import com.netflix.graphql.dgs.internal.InputObjectMapper
import org.springframework.core.KotlinDetector
import org.springframework.core.convert.TypeDescriptor
import org.springframework.core.convert.converter.ConditionalGenericConverter
import org.springframework.core.convert.converter.GenericConverter
import java.util.Optional

internal class InputObjectMapperConverter(private val inputObjectMapper: InputObjectMapper) : ConditionalGenericConverter {
    override fun getConvertibleTypes(): Set<GenericConverter.ConvertiblePair> {
        return setOf(GenericConverter.ConvertiblePair(Map::class.java, Any::class.java))
    }

    override fun matches(sourceType: TypeDescriptor, targetType: TypeDescriptor): Boolean {
        return sourceType.isMap &&
            !targetType.isMap &&
            !targetType.type.isAssignableFrom(Optional::class.java)
    }

    override fun convert(source: Any?, sourceType: TypeDescriptor, targetType: TypeDescriptor): Any {
        @Suppress("unchecked_cast")
        val mapInput = source as Map<String, *>
        return if (KotlinDetector.isKotlinType(targetType.type)) {
            inputObjectMapper.mapToKotlinObject(mapInput, targetType.type.kotlin)
        } else {
            inputObjectMapper.mapToJavaObject(mapInput, targetType.type)
        }
    }
}
