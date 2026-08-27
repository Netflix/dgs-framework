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

package com.netflix.graphql.dgs.internal;

import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KType;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.KTypesJvm;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.ConfigurablePropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DefaultInputObjectMapper implements InputObjectMapper {
    private static final Logger logger = LoggerFactory.getLogger(InputObjectMapper.class);

    private final DefaultConversionService conversionService = new DefaultConversionService();

    public DefaultInputObjectMapper(InputObjectMapper customInputObjectMapper) {
        conversionService.addConverter(
                new Converter(customInputObjectMapper != null ? customInputObjectMapper : this));
    }

    public DefaultInputObjectMapper() {
        this(null);
    }

    private static class Converter implements ConditionalGenericConverter {
        private final InputObjectMapper mapper;

        Converter(InputObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Set<ConvertiblePair> getConvertibleTypes() {
            return Set.of(new ConvertiblePair(Map.class, Object.class));
        }

        @Override
        public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
            if (targetType.getType() == Optional.class) {
                // Let Spring's ObjectToOptionalConverter handle it
                return false;
            }
            if (sourceType.isMap()) {
                TypeDescriptor keyDescriptor = sourceType.getMapKeyTypeDescriptor();
                return keyDescriptor == null || keyDescriptor.getType() == String.class;
            }
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
            Map<String, ?> sourceMap = (Map<String, ?>) source;
            if (KotlinDetector.isKotlinType(targetType.getType())) {
                return mapper.mapToKotlinObject(sourceMap, JvmClassMappingKt.getKotlinClass(targetType.getType()));
            }
            return mapper.mapToJavaObject(sourceMap, targetType.getType());
        }
    }

    @Override
    public <T> T mapToKotlinObject(Map<String, ?> inputMap, KClass<T> targetClass) {
        KFunction<T> constructor = KClasses.getPrimaryConstructor(targetClass);
        if (constructor == null) {
            throw new DgsInvalidInputArgumentException("No primary constructor found for class " + targetClass);
        }

        List<KParameter> parameters = constructor.getParameters();
        Map<KParameter, Object> parametersByName = CollectionUtils.newLinkedHashMap(parameters.size());

        for (KParameter parameter : parameters) {
            String name = parameter.getName();
            if (!inputMap.containsKey(name)) {
                if (parameter.isOptional()) {
                    continue;
                } else if (parameter.getType().isMarkedNullable()) {
                    parametersByName.put(parameter, null);
                    continue;
                }
                throw new DgsInvalidInputArgumentException(
                        "No value specified for required parameter " + name + " of class " + targetClass);
            }

            Object input = inputMap.get(name);
            parametersByName.put(parameter, maybeConvert(input, parameter.getType()));
        }

        try {
            return constructor.callBy(parametersByName);
        } catch (Exception ex) {
            throw new DgsInvalidInputArgumentException(
                    "Provided input arguments do not match arguments of data class `" + targetClass + "`", ex);
        }
    }

    private Object maybeConvert(Object input, KType parameterType) {
        // Check if input is already an instance of the parameter type; we check against the KType / KClass
        // to support inline value classes.
        if (parameterType.getArguments().isEmpty()
                && JvmClassMappingKt.getJavaClass(KTypesJvm.getJvmErasure(parameterType)).isInstance(input)) {
            return input;
        }
        return maybeConvert(input, ReflectJvmMapping.getJavaType(parameterType));
    }

    private Object maybeConvert(Object input, Type parameterType) {
        TypeDescriptor targetType;
        if (parameterType instanceof Class<?> parameterClass) {
            if (parameterClass.isInstance(input)) {
                // No conversion necessary
                return input;
            }
            targetType = TypeDescriptor.valueOf(parameterClass);
        } else {
            targetType = new TypeDescriptor(ResolvableType.forType(parameterType), null, null);
        }
        TypeDescriptor sourceType = TypeDescriptor.forObject(input);

        try {
            return conversionService.convert(input, sourceType, targetType);
        } catch (ConversionException exc) {
            throw new DgsInvalidInputArgumentException("Failed to convert value " + input + " to " + targetType, exc);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T mapToJavaObject(Map<String, ?> inputMap, Class<T> targetClass) {
        if (targetClass.isAssignableFrom(inputMap.getClass())) {
            return (T) inputMap;
        }

        if (targetClass.isRecord()) {
            return handleRecordClass(inputMap, targetClass);
        }

        T instance;
        try {
            Constructor<T> ctor = targetClass.getDeclaredConstructor();
            ctor.trySetAccessible();
            instance = ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new DgsInvalidInputArgumentException(
                    "Failed to instantiate input argument type '" + targetClass + "'", e);
        }
        ConfigurablePropertyAccessor setterAccessor = setterAccessor(instance);
        ConfigurablePropertyAccessor fieldAccessor = fieldAccessor(instance);
        int nrOfPropertyErrors = 0;

        for (Map.Entry<String, ?> entry : inputMap.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            try {
                if (setterAccessor.isWritableProperty(name)) {
                    setterAccessor.setPropertyValue(name, value);
                } else if (fieldAccessor.isWritableProperty(name)) {
                    fieldAccessor.setPropertyValue(name, value);
                } else {
                    nrOfPropertyErrors++;
                    logger.warn(
                            "Field or property '{}' was not found on Input object of type '{}'", name, targetClass);
                }
            } catch (Exception ex) {
                throw new DgsInvalidInputArgumentException("Invalid input argument `" + value
                        + "` for field/property `" + name + "` on type `" + targetClass.getName() + "`", ex);
            }
        }

        // We can't error out if only some fields don't match. This would happen if new schema fields are added, but
        // the Java type wasn't updated yet. If none of the fields match however, it's a pretty good indication that
        // the wrong type was used, hence this check.
        if (!inputMap.isEmpty() && nrOfPropertyErrors == inputMap.size()) {
            throw new DgsInvalidInputArgumentException(
                    "Input argument type '" + targetClass + "' doesn't match input " + inputMap);
        }

        return instance;
    }

    @SuppressWarnings("unchecked")
    private <T> T handleRecordClass(Map<String, ?> inputMap, Class<T> targetClass) {
        RecordComponent[] recordComponents = targetClass.getRecordComponents();
        Object[] args = new Object[recordComponents.length];
        for (int index = 0; index < recordComponents.length; index++) {
            RecordComponent component = recordComponents[index];
            if (inputMap.containsKey(component.getName())) {
                args[index] = maybeConvert(inputMap.get(component.getName()), component.getGenericType());
            }
        }
        Constructor<T> ctor = (Constructor<T>) targetClass.getDeclaredConstructors()[0];
        ctor.trySetAccessible();
        try {
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException exc) {
            throw new DgsInvalidInputArgumentException(
                    "Failed to construct record, class=" + targetClass.getSimpleName(), exc);
        }
    }

    private ConfigurablePropertyAccessor fieldAccessor(Object instance) {
        ConfigurablePropertyAccessor accessor = PropertyAccessorFactory.forDirectFieldAccess(instance);
        accessor.setConversionService(conversionService);
        return accessor;
    }

    private ConfigurablePropertyAccessor setterAccessor(Object instance) {
        ConfigurablePropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(instance);
        accessor.setConversionService(conversionService);
        return accessor;
    }
}
