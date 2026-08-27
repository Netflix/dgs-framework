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

package com.netflix.graphql.dgs.internal.method;

import com.netflix.graphql.dgs.internal.InputObjectMapper;
import kotlin.jvm.JvmClassMappingKt;
import org.springframework.core.KotlinDetector;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

class InputObjectMapperConverter implements ConditionalGenericConverter {
    private final InputObjectMapper inputObjectMapper;

    InputObjectMapperConverter(InputObjectMapper inputObjectMapper) {
        this.inputObjectMapper = inputObjectMapper;
    }

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        return Set.of(new ConvertiblePair(Map.class, Object.class));
    }

    @Override
    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
        return sourceType.isMap()
                && !targetType.isMap()
                && !targetType.getType().isAssignableFrom(Optional.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        Map<String, ?> mapInput = (Map<String, ?>) source;
        if (KotlinDetector.isKotlinType(targetType.getType())) {
            return inputObjectMapper.mapToKotlinObject(
                    mapInput, JvmClassMappingKt.getKotlinClass(targetType.getType()));
        }
        return inputObjectMapper.mapToJavaObject(mapInput, targetType.getType());
    }
}
