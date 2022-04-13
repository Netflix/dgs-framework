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

import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException
import com.netflix.graphql.dgs.exceptions.DgsMissingCookieException
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.slf4j.LoggerFactory
import org.springframework.core.CollectionFactory
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.KotlinDetector
import org.springframework.core.MethodParameter
import org.springframework.core.ResolvableType
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.http.HttpHeaders
import org.springframework.util.MultiValueMap
import org.springframework.util.ObjectUtils
import org.springframework.util.ReflectionUtils
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ValueConstants
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.reflect.full.callSuspend
import kotlin.reflect.jvm.kotlinFunction

class DataFetcherInvoker(
    private val cookieValueResolver: Optional<CookieValueResolver>,
    defaultParameterNameDiscoverer: DefaultParameterNameDiscoverer,
    private val environment: DataFetchingEnvironment,
    private val dgsComponent: Any,
    private val method: Method,
    private val inputObjectMapper: InputObjectMapper,
) {

    private val parameterNames = defaultParameterNameDiscoverer.getParameterNames(method).orEmpty().toList()

    fun invokeDataFetcher(): Any? {
        val args = mutableListOf<Any?>()
        method.parameters.asSequence().filter { it.type != Continuation::class.java }.forEachIndexed { idx, parameter ->

            when {
                parameter.isAnnotationPresent(InputArgument::class.java) -> args.add(
                    processInputArgument(
                        parameter,
                        idx
                    )
                )
                parameter.isAnnotationPresent(RequestHeader::class.java) -> args.add(
                    processRequestHeader(
                        environment,
                        parameter,
                        idx
                    )
                )
                parameter.isAnnotationPresent(RequestParam::class.java) -> args.add(
                    processRequestArgument(
                        environment,
                        parameter,
                        idx
                    )
                )
                parameter.isAnnotationPresent(CookieValue::class.java) -> args.add(
                    processCookieValueArgument(
                        environment,
                        parameter,
                        idx
                    )
                )

                environment.containsArgument(parameterNames[idx]) -> {
                    val parameterValue: Any? = environment.getArgument(parameterNames[idx])
                    val convertedValue = convertValue(
                        parameterValue,
                        ResolvableType.forMethodParameter(MethodParameter.forParameter(parameter))
                    )
                    args += convertedValue
                }

                parameter.type == DataFetchingEnvironment::class.java || parameter.type == DgsDataFetchingEnvironment::class.java -> {
                    args.add(environment)
                }
                else -> {
                    logger.debug(
                        "Unknown argument '{}' on data fetcher {}.{}",
                        parameterNames[idx], dgsComponent.javaClass.name, method.name
                    )
                    // This might cause an exception, but parameter's the best effort we can do
                    args.add(null)
                }
            }
        }

        return if (method.kotlinFunction?.isSuspend == true) {

            val launch = CoroutineScope(Dispatchers.Unconfined).async {
                try {
                    method.kotlinFunction!!.callSuspend(dgsComponent, *args.toTypedArray())
                } catch (exception: InvocationTargetException) {
                    throw exception.cause ?: exception
                }
            }

            launch.asCompletableFuture()
        } else {
            ReflectionUtils.makeAccessible(method)
            ReflectionUtils.invokeMethod(method, dgsComponent, *args.toTypedArray())
        }
    }

    private fun processCookieValueArgument(environment: DataFetchingEnvironment, parameter: Parameter, idx: Int): Any? {
        val requestData = DgsContext.getRequestData(environment)
        val annotation = AnnotationUtils.getAnnotation(parameter, CookieValue::class.java)
            ?: throw AssertionError("Expected parameter ${parameter.name} to have @CookieValue annotation")
        val parameterName = annotation.name.ifBlank { parameterNames[idx] }

        val value = cookieValueResolver.map { resolver -> resolver.getCookieValue(parameterName, requestData) }
            .orElse(if (annotation.defaultValue != ValueConstants.DEFAULT_NONE) annotation.defaultValue else null)

        if (value == null && annotation.required) {
            throw DgsMissingCookieException(parameterName)
        }

        return getValueAsOptional(value, parameter)
    }

    private fun processRequestArgument(environment: DataFetchingEnvironment, parameter: Parameter, idx: Int): Any? {
        val requestData = DgsContext.getRequestData(environment)
        val annotation = AnnotationUtils.getAnnotation(parameter, RequestParam::class.java)
            ?: throw AssertionError("Expected parameter ${parameter.name} to have @RequestParam annotation")

        val parameterName = annotation.name.ifBlank { parameterNames[idx] }
        if (requestData is DgsWebMvcRequestData) {
            val webRequest = requestData.webRequest
            val value: Any? =
                webRequest?.parameterMap?.get(parameterName)?.let {
                    if (parameter.type.isAssignableFrom(List::class.java)) {
                        it
                    } else {
                        it.joinToString()
                    }
                }
                    ?: if (annotation.defaultValue != ValueConstants.DEFAULT_NONE) annotation.defaultValue else null

            if (value == null && annotation.required) {
                throw DgsInvalidInputArgumentException("Required request parameter '$parameterName' was not provided")
            }

            return getValueAsOptional(value, parameter)
        } else {
            logger.warn("@RequestParam is not supported when using WebFlux")
            return null
        }
    }

    private fun processRequestHeader(environment: DataFetchingEnvironment, parameter: Parameter, idx: Int): Any? {
        val requestData = DgsContext.getRequestData(environment)
        val annotation = AnnotationUtils.getAnnotation(parameter, RequestHeader::class.java)
            ?: throw AssertionError("Expected parameter ${parameter.name} to have @RequestHeader annotation")
        val parameterName = annotation.name.ifBlank { parameterNames[idx] }

        if (parameter.type.isAssignableFrom(Map::class.java)) {
            return getValueAsOptional(requestData?.headers?.toSingleValueMap(), parameter)
        } else if (parameter.type.isAssignableFrom(HttpHeaders::class.java) || parameter.type.isAssignableFrom(MultiValueMap::class.java)) {
            return getValueAsOptional(requestData?.headers, parameter)
        }

        val value = requestData?.headers?.get(parameterName)?.let {
            if (parameter.type.isAssignableFrom(List::class.java)) {
                it
            } else {
                it.joinToString()
            }
        } ?: if (annotation.defaultValue != ValueConstants.DEFAULT_NONE) annotation.defaultValue else null

        if (value == null && annotation.required) {
            throw DgsInvalidInputArgumentException("Required header '$parameterName' was not provided")
        }

        return getValueAsOptional(value, parameter)
    }

    private fun processInputArgument(parameter: Parameter, parameterIndex: Int): Any? {
        val annotation = AnnotationUtils.getAnnotation(parameter, InputArgument::class.java)
            ?: throw AssertionError("Expected parameter ${parameter.name} to have @InputArgument annotation")

        val parameterName = annotation.name.ifBlank { parameterNames[parameterIndex] }
        @Suppress("deprecation")
        val collectionType = annotation.collectionType.java

        val parameterValue: Any? = environment.getArgument(parameterName)

        val parameterType = ResolvableType.forMethodParameter(MethodParameter.forParameter(parameter))

        if (collectionType != Any::class.java &&
            parameterType.hasGenerics() &&
            parameterType.getGeneric(0).toClass() != collectionType
        ) {
            throw DgsInvalidInputArgumentException(
                "Collection type specified on @InputArgument annotation does not match actual parameter type. " +
                    "parameter=${parameter.name}, collectionType=${collectionType.name}, actualType=${parameterType.getGeneric(0)}"
            )
        }

        val convertedValue = convertValue(parameterValue, parameterType)

        if (convertedValue == null && environment.fieldDefinition.arguments.none { it.name == parameterName }) {
            logger.warn(
                "Unknown argument '{}' on data fetcher {}.{}",
                parameterName, dgsComponent.javaClass.name, method.name
            )
        }

        return convertedValue
    }

    private fun convertValue(source: Any?, target: ResolvableType): Any? {
        if (source == null) {
            return when (target.toClass()) {
                Optional::class.java -> Optional.empty<Any?>()
                else -> null
            }
        }

        if (target.isInstance(source)) {
            return source
        }

        if (Collection::class.java.isAssignableFrom(target.toClass()) && source is Collection<*>) {
            if (target.toClass().isInstance(source) && source.isEmpty()) {
                return source
            }
            val elementType = target.getGeneric(0)
            val targetCollection = CollectionFactory.createCollection<Any?>(target.toClass(), elementType.toClass(), source.size)
            return source.mapTo(targetCollection) { item ->
                if (!elementType.isInstance(item)) {
                    convertValue(item, elementType)
                } else {
                    item
                }
            }
        }

        if (Map::class.java.isAssignableFrom(target.toClass()) && source is Map<*, *>) {
            if (target.toClass().isInstance(source) && source.isEmpty()) {
                return source
            }
            val keyType = target.getGeneric(0)
            val valueType = target.getGeneric(1)

            val targetMap = CollectionFactory.createMap<Any?, Any?>(target.toClass(), keyType.toClass(), source.size)

            return source.entries.associateTo(targetMap) { (key, value) ->
                val convertedKey = if (!keyType.isInstance(key)) {
                    convertValue(key, keyType)
                } else {
                    key
                }
                val convertedValue = if (!valueType.isInstance(value)) {
                    convertValue(value, valueType)
                } else {
                    value
                }
                convertedKey to convertedValue
            }
        }

        if (target.toClass().isEnum) {
            @Suppress("unchecked_cast")
            val enumClass = target.toClass() as Class<Enum<*>>
            return ObjectUtils.caseInsensitiveValueOf(enumClass.enumConstants, source.toString())
        }

        if (target.toClass() == Optional::class.java) {
            return Optional.ofNullable(convertValue(source, target.getGeneric(0)))
        }

        if (source is Map<*, *>) {
            @Suppress("unchecked_cast")
            val mapInput = source as Map<String, *>
            return if (KotlinDetector.isKotlinType(target.toClass())) {
                inputObjectMapper.mapToKotlinObject(mapInput, target.toClass().kotlin)
            } else {
                inputObjectMapper.mapToJavaObject(mapInput, target.toClass())
            }
        }

        throw DgsInvalidInputArgumentException("Unable to convert from ${source.javaClass} to ${target.toClass()}")
    }

    private fun getValueAsOptional(value: Any?, parameter: Parameter) =
        if (parameter.type.isAssignableFrom(Optional::class.java)) {
            Optional.ofNullable(value)
        } else {
            value
        }

    companion object {
        private val logger = LoggerFactory.getLogger(DataFetcherInvoker::class.java)
    }
}
