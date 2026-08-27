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

import com.netflix.graphql.dgs.Source;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.core.MethodParameter;

public class SourceArgumentResolver implements ArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(Source.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment dfe) {
        Object source = dfe.getSource();
        if (source == null) {
            throw new IllegalArgumentException(
                    "Source is null. Are you trying to use @Source on a root field (e.g. @DgsQuery)?");
        }

        if (parameter.getParameterType().isAssignableFrom(source.getClass())) {
            return source;
        }
        throw new IllegalArgumentException("Invalid source type '" + source.getClass().getName()
                + "'. Expected type '" + parameter.getParameterType().getName() + "'");
    }
}
