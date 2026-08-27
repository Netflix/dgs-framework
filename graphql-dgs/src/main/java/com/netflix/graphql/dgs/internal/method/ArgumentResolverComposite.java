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

import graphql.schema.DataFetchingEnvironment;
import org.springframework.core.MethodParameter;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolves method parameters by delegating to the supplied list of {@link ArgumentResolver argument resolvers}.
 * Previously resolved method parameters are cached.
 */
public class ArgumentResolverComposite implements ArgumentResolver {
    private final List<ArgumentResolver> argumentResolvers;
    private final ConcurrentMap<MethodParameter, ArgumentResolver> argumentResolverCache = new ConcurrentHashMap<>();

    public ArgumentResolverComposite(List<ArgumentResolver> argumentResolvers) {
        this.argumentResolvers = argumentResolvers;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return getArgumentResolver(parameter) != null;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment dfe) {
        ArgumentResolver resolver = getArgumentResolver(parameter);
        if (resolver == null) {
            throw new IllegalArgumentException("Unsupported parameter type [" + parameter.getParameterType().getName()
                    + "]. supportsParameter should be called first.");
        }
        return resolver.resolveArgument(parameter, dfe);
    }

    ArgumentResolver getArgumentResolver(MethodParameter parameter) {
        ArgumentResolver cachedResolver = this.argumentResolverCache.get(parameter);
        if (cachedResolver != null) {
            return cachedResolver;
        }
        for (ArgumentResolver resolver : argumentResolvers) {
            if (resolver.supportsParameter(parameter)) {
                argumentResolverCache.put(parameter, resolver);
                return resolver;
            }
        }
        return null;
    }
}
