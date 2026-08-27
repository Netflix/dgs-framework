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

import com.netflix.graphql.dgs.internal.method.ArgumentResolverComposite;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function2;
import kotlin.reflect.KCallable;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.full.KCallables;
import kotlin.reflect.jvm.ReflectJvmMapping;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.reactor.MonoKt;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.SynthesizingMethodParameter;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataFetcherInvoker implements DataFetcher<Object> {
    private final Object dgsComponent;
    private final ArgumentResolverComposite resolvers;
    private final CoroutineDispatcher coroutineDispatcher;
    private final Method bridgedMethod;
    private final KFunction<?> kotlinFunction;
    private final CompletableFutureWrapper completableFutureWrapper;
    private final List<MethodParameter> methodParameters;

    public DataFetcherInvoker(
            Object dgsComponent,
            Method method,
            ArgumentResolverComposite resolvers,
            ParameterNameDiscoverer parameterNameDiscoverer,
            AsyncTaskExecutor taskExecutor,
            CoroutineDispatcher coroutineDispatcher) {
        this.dgsComponent = dgsComponent;
        this.resolvers = resolvers;
        this.coroutineDispatcher = coroutineDispatcher;
        this.bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
        this.kotlinFunction = KotlinDetector.isKotlinType(bridgedMethod.getDeclaringClass())
                ? ReflectJvmMapping.getKotlinFunction(bridgedMethod)
                : null;
        this.completableFutureWrapper = new CompletableFutureWrapper(taskExecutor);

        List<MethodParameter> parameters = new ArrayList<>();
        for (Parameter parameter : bridgedMethod.getParameters()) {
            MethodParameter methodParameter = SynthesizingMethodParameter.forParameter(parameter);
            methodParameter.initParameterNameDiscovery(parameterNameDiscoverer);
            parameters.add(methodParameter);
        }
        this.methodParameters = List.copyOf(parameters);

        ReflectionUtils.makeAccessible(bridgedMethod);
    }

    public DataFetcherInvoker(
            Object dgsComponent,
            Method method,
            ArgumentResolverComposite resolvers,
            ParameterNameDiscoverer parameterNameDiscoverer,
            AsyncTaskExecutor taskExecutor) {
        this(dgsComponent, method, resolvers, parameterNameDiscoverer, taskExecutor, Dispatchers.getUnconfined());
    }

    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {
        if (methodParameters.isEmpty()) {
            if (completableFutureWrapper.shouldWrapInCompletableFuture(bridgedMethod)) {
                return completableFutureWrapper.wrapInCompletableFuture(
                        () -> ReflectionUtils.invokeMethod(bridgedMethod, dgsComponent));
            }
            try {
                return bridgedMethod.invoke(dgsComponent);
            } catch (Exception exc) {
                return handleReflectionException(exc);
            }
        }

        if (kotlinFunction != null) {
            return invokeKotlinMethod(kotlinFunction, environment);
        }

        Object[] args = new Object[methodParameters.size()];

        for (int idx = 0; idx < methodParameters.size(); idx++) {
            MethodParameter parameter = methodParameters.get(idx);
            if (!resolvers.supportsParameter(parameter)) {
                throw new IllegalStateException(formatArgumentError(parameter, "No suitable resolver"));
            }
            args[idx] = resolvers.resolveArgument(parameter, environment);
        }

        if (completableFutureWrapper.shouldWrapInCompletableFuture(bridgedMethod)) {
            return completableFutureWrapper.wrapInCompletableFuture(
                    () -> ReflectionUtils.invokeMethod(bridgedMethod, dgsComponent, args));
        }
        try {
            return bridgedMethod.invoke(dgsComponent, args);
        } catch (Exception exc) {
            return handleReflectionException(exc);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object invokeKotlinMethod(KFunction<?> kFunc, DataFetchingEnvironment dfe) throws Exception {
        List<KParameter> parameters = kFunc.getParameters();
        Map<KParameter, Object> argsByName = CollectionUtils.newLinkedHashMap(parameters.size());

        int kParameterOffset = 0;
        if (!parameters.isEmpty() && parameters.get(0).getKind() == KParameter.Kind.INSTANCE) {
            argsByName.put(parameters.get(0), dgsComponent);
            kParameterOffset = 1;
        }

        for (int idx = 0; idx < methodParameters.size() && idx + kParameterOffset < parameters.size(); idx++) {
            KParameter kParameter = parameters.get(idx + kParameterOffset);
            MethodParameter parameter = methodParameters.get(idx);
            if (!resolvers.supportsParameter(parameter)) {
                throw new IllegalStateException(formatArgumentError(parameter, "No suitable resolver"));
            }
            Object value = resolvers.resolveArgument(parameter, dfe);
            if (value == null && kParameter.isOptional() && !kParameter.getType().isMarkedNullable()) {
                continue;
            }
            argsByName.put(kParameter, value);
        }

        if (kFunc.isSuspend()) {
            Map<KParameter, Object> suspendArgs = new LinkedHashMap<>(argsByName);
            Function2<Object, Continuation<? super Object>, Object> block =
                    (scope, continuation) -> KCallables.callSuspendBy((KCallable) kFunc, suspendArgs,
                            (Continuation) continuation);
            Mono<Object> mono = MonoKt.mono(coroutineDispatcher, (Function2) block);
            return mono.onErrorMap(
                    InvocationTargetException.class, exc -> ((InvocationTargetException) exc).getTargetException());
        }

        if (completableFutureWrapper.shouldWrapInCompletableFuture(kFunc)) {
            return completableFutureWrapper.wrapInCompletableFuture(() -> kFunc.callBy(argsByName));
        }
        try {
            return kFunc.callBy(argsByName);
        } catch (Exception exc) {
            return handleReflectionException(exc);
        }
    }

    private String formatArgumentError(MethodParameter param, String message) {
        return "Could not resolve parameter [" + param.getParameterIndex() + "] in "
                + param.getExecutable().toGenericString()
                + (!message.isEmpty() ? ": " + message : "");
    }

    /**
     * Handle the given reflection exception.
     *
     * <p>Variant of {@link ReflectionUtils#handleReflectionException} that allows checked exceptions
     * to propagate, but handles {@link NoSuchMethodException}, {@link IllegalAccessException}, and
     * {@link InvocationTargetException} the same way as that helper does; the main difference is that this method
     * will never throw {@link UndeclaredThrowableException}.
     */
    private Object handleReflectionException(Exception exc) throws Exception {
        if (exc instanceof NoSuchMethodException) {
            throw new IllegalStateException("Method not found: " + exc.getMessage());
        }
        if (exc instanceof IllegalAccessException) {
            throw new IllegalStateException("Could not access method or field: " + exc.getMessage());
        }
        if (exc instanceof InvocationTargetException invocationTargetException) {
            Throwable target = invocationTargetException.getTargetException();
            if (target instanceof Exception targetException) {
                throw targetException;
            }
            if (target instanceof Error error) {
                throw error;
            }
            throw new UndeclaredThrowableException(target);
        }
        throw exc;
    }
}
