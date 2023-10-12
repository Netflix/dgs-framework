/*
 * Copyright 2022 Netflix, Inc.
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import org.springframework.core.BridgeMethodResolver
import org.springframework.core.MethodParameter
import org.springframework.core.ParameterNameDiscoverer
import org.springframework.core.annotation.SynthesizingMethodParameter
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.util.CollectionUtils
import org.springframework.util.ReflectionUtils
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.typeOf

class DataFetcherInvoker internal constructor(
    private val dgsComponent: Any,
    method: Method,
    private val resolvers: ArgumentResolverComposite,
    parameterNameDiscoverer: ParameterNameDiscoverer,
    private val taskExecutor: AsyncTaskExecutor?
) : DataFetcher<Any?> {

    private val bridgedMethod: Method = BridgeMethodResolver.findBridgedMethod(method)
    private val kotlinFunction: KFunction<*>? = bridgedMethod.kotlinFunction

    private val methodParameters: List<MethodParameter> = bridgedMethod.parameters.map { parameter ->
        val methodParameter = SynthesizingMethodParameter.forParameter(parameter)
        methodParameter.initParameterNameDiscovery(parameterNameDiscoverer)
        methodParameter
    }

    init {
        ReflectionUtils.makeAccessible(bridgedMethod)
    }

    override fun get(environment: DataFetchingEnvironment): Any? {
        if (methodParameters.isEmpty()) {
            if (shouldWrapInCompletableFuture(bridgedMethod)) {
                return wrapInCompletableFuture { ReflectionUtils.invokeMethod(bridgedMethod, dgsComponent) }
            }
            return ReflectionUtils.invokeMethod(bridgedMethod, dgsComponent)
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

        return if (shouldWrapInCompletableFuture(bridgedMethod)) {
            wrapInCompletableFuture { ReflectionUtils.invokeMethod(bridgedMethod, dgsComponent, *args) }
        } else {
            ReflectionUtils.invokeMethod(bridgedMethod, dgsComponent, *args)
        }
    }

    private fun invokeKotlinMethod(kFunc: KFunction<*>, dfe: DataFetchingEnvironment): Any? {
        val parameters = kFunc.parameters
        val argsByName = CollectionUtils.newLinkedHashMap<KParameter, Any?>(parameters.size)

        val paramSeq = if (parameters[0].kind == KParameter.Kind.INSTANCE) {
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
            return mono(Dispatchers.Unconfined) {
                kFunc.callSuspendBy(argsByName)
            }.onErrorMap(InvocationTargetException::class.java) { it.targetException }
        }
        return try {
            if (shouldWrapInCompletableFuture(kFunc)) {
                wrapInCompletableFuture { kFunc.callBy(argsByName) }
            } else {
                kFunc.callBy(argsByName)
            }
        } catch (ex: Exception) {
            ReflectionUtils.handleReflectionException(ex)
        }
    }

    private fun formatArgumentError(param: MethodParameter, message: String): String {
        return "Could not resolve parameter [${param.parameterIndex}] in " +
            param.executable.toGenericString() + if (message.isNotEmpty()) ": $message" else ""
    }

    /**
     * Wrap the call to a data fetcher in CompletableFuture to enable parallel behavior.
     * Used when virtual threads are enabled.
     */
    private fun wrapInCompletableFuture(function: () -> Any?): Any? {
        return CompletableFuture.supplyAsync({
            return@supplyAsync function.invoke()
        }, taskExecutor)
    }

    /**
     * Decides if a data fetcher method should be wrapped in CompletableFuture automatically.
     * This is only done when a taskExecutor is available, and if the data fetcher doesn't explicitly return CompletableFuture already.
     * Used when virtual threads are enabled.
     */
    private fun shouldWrapInCompletableFuture(kFunc: KFunction<*>): Boolean {
        return taskExecutor != null && !kFunc.returnType.isSubtypeOf(typeOf<CompletionStage<Any>>())
    }

    /**
     * Decides if a data fetcher method should be wrapped in CompletableFuture automatically.
     * This is only done when a taskExecutor is available, and if the data fetcher doesn't explicitly return CompletableFuture already.
     * Used when virtual threads are enabled.
     */
    private fun shouldWrapInCompletableFuture(method: Method): Boolean {
        return taskExecutor != null && !CompletionStage::class.java.isAssignableFrom(method.returnType)
    }
}
