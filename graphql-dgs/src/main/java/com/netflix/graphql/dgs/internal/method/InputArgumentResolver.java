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

import com.netflix.graphql.dgs.InputArgument;
import com.netflix.graphql.dgs.internal.InputObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.MergedAnnotation;

/**
 * Resolves method arguments annotated with {@link InputArgument}.
 *
 * <p>Argument conversion responsibilities are handled by the supplied {@link InputObjectMapper}.
 */
public class InputArgumentResolver extends AbstractInputArgumentResolver {
    public InputArgumentResolver(InputObjectMapper inputObjectMapper) {
        super(inputObjectMapper);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(InputArgument.class);
    }

    @Override
    public String resolveArgumentName(MethodParameter parameter) {
        InputArgument annotation = parameter.getParameterAnnotation(InputArgument.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Unsupported parameter type [" + parameter.getParameterType().getName()
                    + "]. supportsParameter should be called first.");
        }

        InputArgument mergedAnnotation = MergedAnnotation.from(annotation).synthesize();

        String name = mergedAnnotation.name();
        if (name != null && !name.isBlank()) {
            return name;
        }
        String parameterName = parameter.getParameterName();
        if (parameterName == null) {
            throw new IllegalArgumentException("Name for argument of type ["
                    + parameter.getNestedParameterType().getName() + "}"
                    + " not specified, and parameter name information not found in class file either.");
        }
        return parameterName;
    }
}
