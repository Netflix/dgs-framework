/*
 * Copyright 2021 Netflix, Inc.
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

import com.fasterxml.jackson.module.kotlin.isKotlinClass
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
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.util.ReflectionUtils
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ValueConstants
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.reflect.full.callSuspend
import kotlin.reflect.jvm.kotlinFunction

class DataFetcherInvoker(
    private val cookieValueResolver: Optional<CookieValueResolver>,
    defaultParameterNameDiscoverer: DefaultParameterNameDiscoverer,
    private val environment: DataFetchingEnvironment,
    private val dgsComponent: Any,
    private val method: Method
) {

    private val parameterNames = defaultParameterNameDiscoverer.getParameterNames(method) ?: emptyArray()

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
                    val parameterValue: Any = environment.getArgument(parameterNames[idx])
                    val convertValue = convertValue(parameterValue, parameter, null)
                    args.add(convertValue)
                }

                parameter.type == DataFetchingEnvironment::class.java || parameter.type == DgsDataFetchingEnvironment::class.java -> {
                    args.add(environment)
                }
                else -> {
                    logger.warn("Unknown argument '${parameterNames[idx]}' on data fetcher ${dgsComponent.javaClass.name}.${method.name}")
                    // This might cause an exception, but parameter's the best effort we can do
                    args.add(null)
                }
            }
        }

        return if (method.kotlinFunction?.isSuspend == true) {

            val launch = CoroutineScope(Dispatchers.Unconfined).async {
                return@async method.kotlinFunction!!.callSuspend(dgsComponent, *args.toTypedArray())
            }

            launch.asCompletableFuture()
        } else {
            ReflectionUtils.invokeMethod(method, dgsComponent, *args.toTypedArray())
        }
    }

    private fun processCookieValueArgument(environment: DataFetchingEnvironment, parameter: Parameter, idx: Int): Any? {
        val requestData = DgsContext.getRequestData(environment)
        val annotation = AnnotationUtils.getAnnotation(parameter, CookieValue::class.java)!!
        val name: String = AnnotationUtils.getAnnotationAttributes(annotation)["name"] as String
        val parameterName = name.ifBlank { parameterNames[idx] }
        val value = if (cookieValueResolver.isPresent) {
            cookieValueResolver.get().getCookieValue(parameterName, requestData)
        } else {
            null
        }
            ?: if (annotation.defaultValue != ValueConstants.DEFAULT_NONE) annotation.defaultValue else null

        if (value == null && annotation.required) {
            throw DgsMissingCookieException(parameterName)
        }

        return getValueAsOptional(value, parameter)
    }

    private fun processRequestArgument(environment: DataFetchingEnvironment, parameter: Parameter, idx: Int): Any? {
        val requestData = DgsContext.getRequestData(environment)
        val annotation = AnnotationUtils.getAnnotation(parameter, RequestParam::class.java)!!
        val name: String = AnnotationUtils.getAnnotationAttributes(annotation)["name"] as String
        val parameterName = name.ifBlank { parameterNames[idx] }
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
        val annotation = AnnotationUtils.getAnnotation(parameter, RequestHeader::class.java)!!
        val name: String = AnnotationUtils.getAnnotationAttributes(annotation)["name"] as String
        val parameterName = name.ifBlank { parameterNames[idx] }
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
        val annotation = AnnotationUtils.getAnnotation(parameter, InputArgument::class.java)!!
        val name: String = AnnotationUtils.getAnnotationAttributes(annotation)["name"] as String

        val parameterName = name.ifBlank { parameterNames[parameterIndex] }
        val collectionType = annotation.collectionType.java
        val parameterValue: Any? = environment.getArgument(parameterName)

        val convertValue: Any? = if (parameterValue is List<*> && collectionType != Object::class.java) {
            try {
                // Return a list of elements that are converted to their collection type, e.e.g. List<Person>, List<String> etc.
                parameterValue.map { item -> convertValue(item, parameter, collectionType) }.toList()
            } catch (ex: Exception) {
                throw DgsInvalidInputArgumentException(
                    "Specified type '$collectionType' is invalid for $parameterName.",
                    ex
                )
            }
        } else if (parameterValue is Map<*, *> && parameter.type.isAssignableFrom(Map::class.java)) {
            parameterValue
        } else {
            // Return the converted value mapped to the defined type
            convertValue(parameterValue, parameter, collectionType)
        }

        val paramType = parameter.type
        val optionalValue = getValueAsOptional(convertValue, parameter)

        if (optionalValue != null && !paramType.isPrimitive && !paramType.isAssignableFrom(optionalValue.javaClass)) {
            throw DgsInvalidInputArgumentException("Specified type '${parameter.type}' is invalid. Found ${parameterValue?.javaClass?.name} instead.")
        }

        if (convertValue == null && environment.fieldDefinition.arguments.none { it.name == parameterName }) {
            logger.warn("Unknown argument '$parameterName' on data fetcher ${dgsComponent.javaClass.name}.${method.name}")
        }

        return optionalValue
    }

    private fun convertValue(parameterValue: Any?, parameter: Parameter, collectionType: Class<out Any>?) =
        if (parameterValue is Map<*, *>) {
            // Account for Optional
            val targetType = if (parameter.type.isAssignableFrom(Optional::class.java) || parameter.type.isAssignableFrom(List::class.java) || parameter.type.isAssignableFrom(Set::class.java)) {
                if (collectionType != null && collectionType != Object::class.java) {
                    collectionType
                } else {
                    throw DgsInvalidInputArgumentException("When ${parameter.type.simpleName}<T> is used, the type must be specified using the collectionType argument of the @InputArgument annotation.")
                }
            } else {
                parameter.type
            }

            if (targetType.isKotlinClass()) {
                InputObjectMapper.mapToKotlinObject(parameterValue as Map<String, *>, targetType.kotlin)
            } else {
                InputObjectMapper.mapToJavaObject(parameterValue as Map<String, *>, targetType)
            }
        } else if (parameter.type.isEnum && parameterValue !== null) {
            (parameter.type.enumConstants as Array<Enum<*>>).find { it.name == parameterValue }
                ?: throw DgsInvalidInputArgumentException("Invalid enum value '$parameterValue for enum type ${parameter.type.name}")
        } else if (parameter.type == Optional::class.java) {
            val targetType: Class<*> = if (collectionType != Object::class.java) {
                collectionType!!
            } else {
                (parameter.parameterizedType as ParameterizedType).actualTypeArguments[0] as Class<*>
            }

            if (targetType.isEnum) {
                (targetType.enumConstants as Array<Enum<*>>).find { it.name == parameterValue }
            } else {
                parameterValue
            }
        } else {
            if (parameterValue is List<*> && parameter.type == Set::class.java) {
                parameterValue.toSet()
            } else {
                parameterValue
            }
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
