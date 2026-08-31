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

import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;

/**
 * Resolves method arguments for parameters of type {@link DataFetchingEnvironment}
 * or {@link DgsDataFetchingEnvironment}.
 */
public class DataFetchingEnvironmentArgumentResolver implements ArgumentResolver {
    private final ApplicationContext ctx;

    public DataFetchingEnvironmentArgumentResolver(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == DgsDataFetchingEnvironment.class
                || parameter.getParameterType() == DataFetchingEnvironment.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment dfe) {
        if (parameter.getParameterType() == DgsDataFetchingEnvironment.class
                && !(dfe instanceof DgsDataFetchingEnvironment)) {
            return new DgsDataFetchingEnvironment(dfe, ctx);
        }
        return dfe;
    }
}
