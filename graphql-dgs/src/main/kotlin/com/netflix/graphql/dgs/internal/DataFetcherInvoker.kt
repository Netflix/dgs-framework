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

package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.internal.method.ArgumentResolverComposite
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import org.springframework.core.BridgeMethodResolver
import org.springframework.core.KotlinDetector
import org.springframework.core.MethodParameter
import org.springframework.core.ParameterNameDiscoverer
import org.springframework.core.annotation.SynthesizingMethodParameter
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.util.CollectionUtils
import org.springframework.util.ReflectionUtils
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.UndeclaredThrowableException
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.jvm.kotlinFunction

class DataFetcherInvoker internal constructor(
    private val dgsComponent: Any,
    method: Method,
    private val resolvers: ArgumentResolverComposite,
    parameterNameDiscoverer: ParameterNameDiscoverer,
    taskExecutor: AsyncTaskExecutor?,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) : DataFetcher<Any?> {
    private val bridgedMethod: Method = BridgeMethodResolver.findBridgedMethod(method)
    private val kotlinFunction: KFunction<*>? =
        if (KotlinDetector.isKotlinType(
                bridgedMethod.declaringClass,
            )
        ) {
            bridgedMethod.kotlinFunction
        } else {
            null
        }
    private val completableFutureWrapper = CompletableFutureWrapper(taskExecutor)

    private val methodParameters: List<MethodParameter> =
        bridgedMethod.parameters.map { parameter ->
            val methodParameter = SynthesizingMethodParameter.forParameter(parameter)
            methodParameter.initParameterNameDiscovery(parameterNameDiscoverer)
            methodParameter
        }

    init {
        ReflectionUtils.makeAccessible(bridgedMethod)
    }

    @Throws(Exception::class)
    override fun get(environment: DataFetchingEnvironment): Any? {
        if (methodParameters.isEmpty()) {
            if (completableFutureWrapper.shouldWrapInCompletableFuture(bridgedMethod)) {
                return completableFutureWrapper.wrapInCompletableFuture { ReflectionUtils.invokeMethod(bridgedMethod, dgsComponent) }
            }
            return try {
                bridgedMethod.invoke(dgsComponent)
            } catch (exc: Exception) {
                handleReflectionException(exc)
            }
        }

        if (kotlinFunction != null) {
            return invokeKotlinMethod(kotlinFunction, environment)
        }

        val args = arrayOfNulls<Any?>(methodParameters.size)

        for ((idx, parameter) in methodParameters.withIndex()) {
            if (!resolvers.supportsParameter(parameter)) {
                throw IllegalStateException(formatArgumentError(parameter, "No suitable resolver"))
            }
            args[idx] = resolvers.resolveArgument(parameter, environment)
        }

        return if (completableFutureWrapper.shouldWrapInCompletableFuture(bridgedMethod)) {
            completableFutureWrapper.wrapInCompletableFuture { ReflectionUtils.invokeMethod(bridgedMethod, dgsComponent, *args) }
        } else {
            try {
                bridgedMethod.invoke(dgsComponent, *args)
            } catch (exc: Exception) {
                handleReflectionException(exc)
            }
        }
    }

    private fun invokeKotlinMethod(
        kFunc: KFunction<*>,
        dfe: DataFetchingEnvironment,
    ): Any? {
        val parameters = kFunc.parameters
        val argsByName = CollectionUtils.newLinkedHashMap<KParameter, Any?>(parameters.size)

        val paramSeq =
            if (parameters[0].kind == KParameter.Kind.INSTANCE) {
                argsByName[parameters[0]] = dgsComponent
                parameters.asSequence().drop(1)
            } else {
                parameters.asSequence()
            }

        for ((kParameter, parameter) in paramSeq.zip(methodParameters.asSequence())) {
            if (!resolvers.supportsParameter(parameter)) {
                throw IllegalStateException(formatArgumentError(parameter, "No suitable resolver"))
            }
            val value = resolvers.resolveArgument(parameter, dfe)
            if (value == null && kParameter.isOptional && !kParameter.type.isMarkedNullable) {
                continue
            }
            argsByName[kParameter] = value
        }

        if (kFunc.isSuspend) {
            return mono(coroutineDispatcher) {
                kFunc.callSuspendBy(argsByName)
            }.onErrorMap(InvocationTargetException::class.java) { it.targetException }
        }
        return if (completableFutureWrapper.shouldWrapInCompletableFuture(kFunc)) {
            completableFutureWrapper.wrapInCompletableFuture { kFunc.callBy(argsByName) }
        } else {
            try {
                kFunc.callBy(argsByName)
            } catch (exc: Exception) {
                handleReflectionException(exc)
            }
        }
    }

    private fun formatArgumentError(
        param: MethodParameter,
        message: String,
    ): String =
        "Could not resolve parameter [${param.parameterIndex}] in " +
            param.executable.toGenericString() + if (message.isNotEmpty()) ": $message" else ""

    /**
     * Handle the given reflection exception.
     *
     * Variant of [ReflectionUtils.handleReflectionException] that allows checked exceptions
     * to propagate, but handles [NoSuchMethodException], [IllegalAccessException], and [InvocationTargetException]
     * the same way as that helper does; the main difference is that this method that this method will never throw
     * [UndeclaredThrowableException].
     */
    private fun handleReflectionException(exc: Exception): Nothing {
        if (exc is NoSuchMethodException) {
            throw IllegalStateException("Method not found: ${exc.message}")
        }
        if (exc is IllegalAccessException) {
            throw IllegalStateException("Could not access method or field: ${exc.message}")
        }
        if (exc is InvocationTargetException) {
            throw exc.targetException
        }
        throw exc
    }
}
