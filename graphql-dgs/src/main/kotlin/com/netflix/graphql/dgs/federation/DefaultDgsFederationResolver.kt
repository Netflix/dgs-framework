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

package com.netflix.graphql.dgs.federation

import com.apollographql.federation.graphqljava._Entity
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsFederationResolver
import com.netflix.graphql.dgs.exceptions.InvalidDgsEntityFetcher
import com.netflix.graphql.dgs.exceptions.MissingDgsEntityFetcherException
import com.netflix.graphql.dgs.exceptions.MissingFederatedQueryArgument
import com.netflix.graphql.dgs.internal.EntityFetcherRegistry
import com.netflix.graphql.types.errors.TypedGraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherResult
import graphql.execution.ExecutionStepInfo
import graphql.execution.ResultPath
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingEnvironmentImpl
import graphql.schema.TypeResolver
import org.dataloader.Try
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import reactor.core.publisher.Mono
import java.lang.reflect.InvocationTargetException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage

@DgsComponent
open class DefaultDgsFederationResolver() :
    DgsFederationResolver {

    /**
     * This constructor is used by DgsSchemaProvider when no custom DgsFederationResolver is provided.
     * This is the most common use case.
     * The default constructor is used to extend the DefaultDgsFederationResolver. In that case injection is used to provide the schemaProvider.
     */
    constructor(
        entityFetcherRegistry: EntityFetcherRegistry,
        dataFetcherExceptionHandler: Optional<DataFetcherExceptionHandler>
    ) : this() {
        this.entityFetcherRegistry = entityFetcherRegistry
        dgsExceptionHandler = dataFetcherExceptionHandler
    }

    /**
     * Used when the DefaultDgsFederationResolver is extended.
     */
    @Suppress("JoinDeclarationAndAssignment")
    @Autowired
    lateinit var entityFetcherRegistry: EntityFetcherRegistry

    @Autowired
    lateinit var dgsExceptionHandler: Optional<DataFetcherExceptionHandler>

    override fun entitiesFetcher(): DataFetcher<Any?> {
        return DataFetcher { env ->
            dgsEntityFetchers(env)
        }
    }

    private fun dgsEntityFetchers(env: DataFetchingEnvironment): CompletableFuture<DataFetcherResult<List<Any?>>> {
        val resultList = env.getArgument<List<Map<String, Any>>>(_Entity.argumentName)
            .map { values ->
                Try.tryCall {
                    val typename = values["__typename"]
                        ?: throw MissingFederatedQueryArgument("__typename")
                    val fetcher = entityFetcherRegistry.entityFetchers[typename]
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
                        logger.error("@DgsEntityFetcher returned null for type: $typename")
                        CompletableFuture.completedFuture(null)
                    }

                    when (result) {
                        is CompletionStage<*> -> result.toCompletableFuture()
                        is Mono<*> -> result.toFuture()
                        else -> CompletableFuture.completedFuture(result)
                    }
                }
                    .map { tryFuture -> Try.tryFuture(tryFuture) }
                    .recover { exception -> CompletableFuture.completedFuture(Try.failed(exception)) }
                    .get()
            }

        return CompletableFuture.allOf(*resultList.toTypedArray()).thenApply {
            val trySequence = resultList.asSequence().map { it.join() }
            DataFetcherResult.newResult<List<Any?>>()
                .data(
                    trySequence
                        .map { tryResult -> tryResult.orElse(null) }
                        .flatMap { r -> if (r is Collection<*>) r.asSequence() else sequenceOf(r) }
                        .toList()
                )
                .errors(
                    trySequence
                        .mapIndexed { index, tryResult -> Pair(index, tryResult) }
                        .filter { iter -> iter.second.isFailure }
                        .map { iter -> Pair(iter.first, iter.second.throwable) }
                        .flatMap { iter: Pair<Int, Throwable> ->
                            // extract exception from known wrapper types
                            val exception = when {
                                iter.second is InvocationTargetException && (iter.second as InvocationTargetException).targetException != null -> (iter.second as InvocationTargetException).targetException
                                iter.second is CompletionException && iter.second.cause != null -> iter.second.cause!!
                                else -> iter.second
                            }
                            // handle the exception (using the custom handler if present)
                            if (dgsExceptionHandler.isPresent) {
                                val dfeWithErrorPath = createDataFetchingEnvironmentWithPath(env, iter.first)
                                val res = dgsExceptionHandler.get().handleException(
                                    DataFetcherExceptionHandlerParameters
                                        .newExceptionParameters()
                                        .dataFetchingEnvironment(dfeWithErrorPath)
                                        .exception(exception)
                                        .build()
                                )
                                res.join().errors.asSequence()
                            } else {
                                sequenceOf(
                                    TypedGraphQLError.newInternalErrorBuilder()
                                        .message("%s: %s", exception::class.java.name, exception.message)
                                        .path(ResultPath.parse("/_entities,${iter.first}"))
                                        .build()
                                )
                            }
                        }
                        .toList()
                )
                .build()
        }
    }

    open fun createDataFetchingEnvironmentWithPath(env: DataFetchingEnvironment, pathIndex: Int): DgsDataFetchingEnvironment {
        val pathWithIndex = env.executionStepInfo.path.segment("$pathIndex")
        val executionStepInfoWithPath = ExecutionStepInfo.newExecutionStepInfo(env.executionStepInfo).path(pathWithIndex).build()
        return DgsDataFetchingEnvironment(
            DataFetchingEnvironmentImpl
                .newDataFetchingEnvironment()
                .arguments(env.arguments)
                .dataLoaderRegistry(env.dataLoaderRegistry)
                .graphQLContext(env.graphQlContext)
                .root(env.getRoot())
                .graphQLSchema(env.graphQLSchema)
                .fragmentsByName(env.fragmentsByName)
                .dataLoaderRegistry(env.dataLoaderRegistry)
                .locale(env.locale)
                .document(env.document)
                .operationDefinition(env.operationDefinition)
                .variables(env.variables)
                .executionId(env.executionId)
                .executionStepInfo(executionStepInfoWithPath)
                .build()
        )
    }

    open fun typeMapping(): Map<Class<*>, String> {
        return emptyMap()
    }

    override fun typeResolver(): TypeResolver {
        return TypeResolver { env ->
            val src: Any = env.getObject()

            val typeName =
                if (typeMapping().containsKey(src::class.java)) {
                    typeMapping()[src::class.java]
                } else {
                    src::class.java.simpleName
                }

            val type = env.schema.getObjectType(typeName)
            if (type == null) {
                logger.warn(
                    "No type definition found for {}. You probably need to provide either a type mapping," +
                        "or override DefaultDgsFederationResolver.typeResolver()." +
                        "Alternatively make sure the type name in the schema and your Java model match",
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
