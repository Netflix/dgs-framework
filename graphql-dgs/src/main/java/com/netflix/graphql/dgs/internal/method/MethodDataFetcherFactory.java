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

import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.internal.DataFetcherInvoker;
import graphql.TrivialDataFetcher;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.Dispatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.task.AsyncTaskExecutor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Factory for constructing a {@link DataFetcher} given a {@link DgsData} annotated method.
 *
 * <p>Resolving of method arguments is handled by the supplied {@link ArgumentResolver argument resolvers}.
 */
public class MethodDataFetcherFactory {
    private final ParameterNameDiscoverer parameterNameDiscoverer;
    private final AsyncTaskExecutor asyncTaskExecutor;
    private final CoroutineDispatcher coroutineDispatcher;
    private final ArgumentResolverComposite resolvers;

    public MethodDataFetcherFactory(
            List<ArgumentResolver> argumentResolvers,
            ParameterNameDiscoverer parameterNameDiscoverer,
            AsyncTaskExecutor asyncTaskExecutor,
            CoroutineDispatcher coroutineDispatcher) {
        this.parameterNameDiscoverer = parameterNameDiscoverer;
        this.asyncTaskExecutor = asyncTaskExecutor;
        this.coroutineDispatcher = coroutineDispatcher;
        this.resolvers = new ArgumentResolverComposite(argumentResolvers);
    }

    public MethodDataFetcherFactory(
            List<ArgumentResolver> argumentResolvers,
            ParameterNameDiscoverer parameterNameDiscoverer,
            AsyncTaskExecutor asyncTaskExecutor) {
        this(argumentResolvers, parameterNameDiscoverer, asyncTaskExecutor, Dispatchers.getUnconfined());
    }

    public MethodDataFetcherFactory(
            List<ArgumentResolver> argumentResolvers, ParameterNameDiscoverer parameterNameDiscoverer) {
        this(argumentResolvers, parameterNameDiscoverer, null, Dispatchers.getUnconfined());
    }

    /** Constructor used by Spring; resolves all {@link ArgumentResolver} beans in order. */
    @Autowired
    public MethodDataFetcherFactory(ObjectProvider<ArgumentResolver> argumentResolvers) {
        this(argumentResolvers.orderedStream().toList(), new DefaultParameterNameDiscoverer(), null,
                Dispatchers.getUnconfined());
    }

    public MethodDataFetcherFactory(List<ArgumentResolver> argumentResolvers) {
        this(argumentResolvers, new DefaultParameterNameDiscoverer(), null, Dispatchers.getUnconfined());
    }

    public ParameterNameDiscoverer getParameterNameDiscoverer() {
        return parameterNameDiscoverer;
    }

    public DataFetcher<Object> createDataFetcher(Object bean, Method method, FieldCoordinates fieldCoordinates) {
        if (isTrivial(method, fieldCoordinates)) {
            DataFetcherInvoker methodDataFetcher = new DataFetcherInvoker(
                    bean, method, resolvers, parameterNameDiscoverer, null, coroutineDispatcher);
            return new TrivialDataFetcher<>() {
                @Override
                public Object get(DataFetchingEnvironment environment) throws Exception {
                    return methodDataFetcher.get(environment);
                }

                @Override
                public String toString() {
                    return "TrivialMethodDataFetcher{field=" + fieldCoordinates + "}";
                }
            };
        }

        return new DataFetcherInvoker(
                bean, method, resolvers, parameterNameDiscoverer, asyncTaskExecutor, coroutineDispatcher);
    }

    public ArgumentResolver getSelectedArgumentResolver(MethodParameter methodParameter) {
        return resolvers.getArgumentResolver(methodParameter);
    }

    private boolean isTrivial(Method method, FieldCoordinates coordinates) {
        Optional<MergedAnnotation<DgsData>> annotation = MergedAnnotations
                .from(method)
                .stream(DgsData.class)
                .filter(candidate -> candidate.getString("parentType").equals(coordinates.getTypeName())
                        && candidate.getString("field").equals(coordinates.getFieldName()))
                .findFirst();
        return annotation.map(a -> a.getBoolean("trivial")).orElse(false);
    }
}
