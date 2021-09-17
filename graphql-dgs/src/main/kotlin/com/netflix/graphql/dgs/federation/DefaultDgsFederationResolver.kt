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

package com.netflix.graphql.dgs.federation

import com.apollographql.federation.graphqljava._Entity
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsFederationResolver
import com.netflix.graphql.dgs.exceptions.InvalidDgsEntityFetcher
import com.netflix.graphql.dgs.exceptions.MissingDgsEntityFetcherException
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.types.errors.TypedGraphQLError
import graphql.GraphQLError
import graphql.execution.*
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.TypeResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.lang.reflect.InvocationTargetException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@DgsComponent
open class DefaultDgsFederationResolver() :
    DgsFederationResolver {

    /**
     * This constructor is used by DgsSchemaProvider when no custom DgsFederationResolver is provided.
     * This is the most common use case.
     * The default constructor is used to extend the DefaultDgsFederationResolver. In that case injection is used to provide the schemaProvider.
     */
    constructor(providedDgsSchemaProvider: DgsSchemaProvider, dataFetcherExceptionHandler: Optional<DataFetcherExceptionHandler>) : this() {
        dgsSchemaProvider = providedDgsSchemaProvider
        dgsExceptionHandler = dataFetcherExceptionHandler
    }

    /**
     * Used when the DefaultDgsFederationResolver is extended.
     */
    @Suppress("JoinDeclarationAndAssignment")
    @Autowired
    lateinit var dgsSchemaProvider: DgsSchemaProvider

    @Autowired
    lateinit var dgsExceptionHandler: Optional<DataFetcherExceptionHandler>

    override fun entitiesFetcher(): DataFetcher<Any?> {
        return DataFetcher { env ->
            dgsEntityFetchers(env)
        }
    }

    private fun dgsEntityFetchers(env: DataFetchingEnvironment): CompletableFuture<DataFetcherResult<List<Any?>>> {
        val errorsList = mutableListOf<GraphQLError>()
        val resultList = env.getArgument<List<Map<String, Any>>>(_Entity.argumentName)
            .map { values ->
                try {
                    val typename = values["__typename"]
                        ?: throw RuntimeException("__typename missing from arguments in federated query")
                    val fetcher = dgsSchemaProvider.entityFetchers[typename]
                        ?: throw MissingDgsEntityFetcherException(typename.toString())

                    if (!fetcher.second.parameterTypes.any { it.isAssignableFrom(Map::class.java) }) {
                        throw InvalidDgsEntityFetcher("@DgsEntityFetcher ${fetcher.first::class.java.name}.${fetcher.second.name} is invalid. A DgsEntityFetcher must accept an argument of type Map<String, Object>")
                    }
                    val result =
                        if (fetcher.second.parameterTypes.any { it.isAssignableFrom(DgsDataFetchingEnvironment::class.java) }) {
                            fetcher.second.invoke(fetcher.first, values, DgsDataFetchingEnvironment(env))
                        } else {
                            fetcher.second.invoke(fetcher.first, values)
                        }

                    if (result == null) {
                        CompletableFuture.completedFuture(null)
                    }

                    if (result is CompletionStage<*>) {
                        result.toCompletableFuture()
                    } else {
                        CompletableFuture.completedFuture(result)
                    }
                } catch (e: Exception) {
                    if (e is InvocationTargetException && e.targetException != null) {
                        if (dgsExceptionHandler.isPresent) {
                            val res = dgsExceptionHandler.get().onException(
                                DataFetcherExceptionHandlerParameters.newExceptionParameters()
                                    .dataFetchingEnvironment(env).exception(e.targetException).build()
                            )
                            res.errors.forEach { errorsList.add(it) }
                        } else {
                            errorsList.add(
                                TypedGraphQLError.newInternalErrorBuilder()
                                    .message("%s: %s", e.targetException::class.java.name, e.targetException.message)
                                    .path(ResultPath.parse("/_entities")).build()
                            )
                        }
                    } else {
                        errorsList.add(
                            TypedGraphQLError.newInternalErrorBuilder().message("%s: %s", e::class.java.name, e.message)
                                .path(ResultPath.parse("/_entities")).build()
                        )
                    }
                    CompletableFuture.completedFuture(null)
                }
            }

        return CompletableFuture.allOf(*resultList.toTypedArray()).thenApply {
            DataFetcherResult.newResult<List<Any?>>().data(
                resultList.asSequence()
                    .map { cf -> cf.join() }
                    .flatMap { r -> if (r is Collection<*>) r.asSequence() else sequenceOf(r) }
                    .toList()
            )
                .errors(errorsList)
                .build()
        }
    }

    open fun typeMapping(): Map<Class<*>, String> {
        return emptyMap()
    }

    override fun typeResolver(): TypeResolver {
        return TypeResolver { env ->
            val src: Any = env.getObject()

            val typeName =
                if (typeMapping().containsKey(src::class.java)) typeMapping()[src::class.java] else src::class.java.simpleName
            val type = env.schema.getObjectType(typeName)
            if (type == null) {
                logger.warn(
                    "No type definition found for {}. You probably need to provide either a type mapping, or override DefaultDgsFederationResolver.typeResolver(). Alternatively make sure the type name in the schema and your Java model match",
                    src::class.java.name
                )
            }

            type
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DefaultDgsFederationResolver::class.java)
    }
}
