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

import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException;
import com.netflix.graphql.dgs.internal.InputObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLArgument;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class AbstractInputArgumentResolver implements ArgumentResolver {
    private static final Logger logger = LoggerFactory.getLogger(AbstractInputArgumentResolver.class);

    private final DefaultConversionService conversionService = new DefaultConversionService();
    private final ConcurrentMap<MethodParameter, String> argumentNameCache = new ConcurrentHashMap<>();

    protected AbstractInputArgumentResolver(InputObjectMapper inputObjectMapper) {
        conversionService.addConverter(new InputObjectMapperConverter(inputObjectMapper));
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment dfe) {
        String argumentName = getArgumentName(parameter);
        if (argumentName == null) {
            return null;
        }
        Object value = dfe.getArgument(argumentName);

        Method method = parameter.getMethod();
        KFunction<?> kfunc = method != null ? ReflectJvmMapping.getKotlinFunction(method) : null;
        if (kfunc != null) {
            List<KParameter> kParameters = kfunc.getParameters();
            int parameterIdx = kParameters.get(0).getKind() == KParameter.Kind.INSTANCE
                    ? parameter.getParameterIndex() + 1
                    : parameter.getParameterIndex();
            KParameter param = kParameters.get(parameterIdx);
            if (param.getType().getArguments().isEmpty()
                    && kotlin.jvm.JvmClassMappingKt.getJavaClass(
                                    kotlin.reflect.jvm.KTypesJvm.getJvmErasure(param.getType()))
                            .isInstance(value)) {
                return value;
            }
        }

        TypeDescriptor typeDescriptor = new TypeDescriptor(parameter);
        Object convertedValue = convertValue(value, typeDescriptor);

        if (convertedValue == null
                && dfe.getFieldDefinition().getArguments().stream()
                        .noneMatch(argument -> argument.getName().equals(argumentName))) {
            logger.warn("Unknown argument '{}'", argumentName);
        }

        return convertedValue;
    }

    public abstract String resolveArgumentName(MethodParameter parameter);

    private String getArgumentName(MethodParameter parameter) {
        String argumentName = argumentNameCache.get(parameter);
        if (argumentName != null) {
            return argumentName;
        }
        String name = resolveArgumentName(parameter);
        if (name == null) {
            return null;
        }
        argumentNameCache.put(parameter, name);
        return name;
    }

    private Object convertValue(Object source, TypeDescriptor target) {
        if (target.getResolvableType().isInstance(source)) {
            return source;
        }

        TypeDescriptor sourceType = TypeDescriptor.forObject(source);
        if (conversionService.canConvert(sourceType, target)) {
            return conversionService.convert(source, sourceType, target);
        }

        throw new DgsInvalidInputArgumentException(
                "Unable to convert from " + (source != null ? source.getClass() : null) + " to " + target.getType());
    }
}
