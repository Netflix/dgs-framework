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

package com.netflix.graphql.dgs.mvc.internal.method;

import com.netflix.graphql.dgs.context.DgsContext;
import com.netflix.graphql.dgs.internal.DgsRequestData;
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData;
import com.netflix.graphql.dgs.internal.method.ArgumentResolver;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

/**
 * {@link ArgumentResolver} adapter for Spring's {@link HandlerMethodArgumentResolver}.
 * Allows leveraging Spring MVC adapters for things such as @CookieValue annotated methods.
 */
public class HandlerMethodArgumentResolverAdapter implements ArgumentResolver {
    private final HandlerMethodArgumentResolver delegate;
    private final WebDataBinderFactory webDataBinderFactory;

    public HandlerMethodArgumentResolverAdapter(
            HandlerMethodArgumentResolver delegate, WebDataBinderFactory webDataBinderFactory) {
        this.delegate = delegate;
        this.webDataBinderFactory = webDataBinderFactory;
    }

    public HandlerMethodArgumentResolverAdapter(HandlerMethodArgumentResolver delegate) {
        this(delegate, null);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return delegate.supportsParameter(parameter);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment dfe) {
        try {
            return delegate.resolveArgument(parameter, null, getRequest(dfe), webDataBinderFactory);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(ex);
        }
    }

    private NativeWebRequest getRequest(DataFetchingEnvironment dfe) {
        DgsRequestData requestData = DgsContext.getRequestData(dfe);
        if (!(requestData instanceof DgsWebMvcRequestData webMvcRequestData)) {
            throw new AssertionError();
        }
        return (NativeWebRequest) webMvcRequestData.getWebRequest();
    }
}
