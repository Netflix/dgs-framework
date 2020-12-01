/*
 * Copyright 2020 Netflix, Inc.
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
import com.netflix.graphql.dgs.DgsFederationResolver
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.TypeResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors
import java.util.stream.Stream

@DgsComponent
open class DefaultDgsFederationResolver() : DgsFederationResolver {
    constructor(providedDgsSchemaProvider: DgsSchemaProvider): this() {
        dgsSchemaProvider = providedDgsSchemaProvider
    }

    val logger: Logger = LoggerFactory.getLogger(DefaultDgsFederationResolver::class.java)

    @Autowired
    lateinit var dgsSchemaProvider: DgsSchemaProvider

    override fun entitiesFetcher(): DataFetcher<Any?> {
        return DataFetcher { env ->
            dgsEntityFetchers(env)
        }
    }

    private fun dgsEntityFetchers(env: DataFetchingEnvironment): CompletableFuture<List<Any?>> {
        val resultList = env.getArgument<List<Map<String, Any>>>(_Entity.argumentName)
                .stream()
                .map { values ->
                    val fetcher = dgsSchemaProvider.entityFetchers[values["__typename"]]

                    val result = if (fetcher?.second?.parameterTypes?.contains(DataFetchingEnvironment::class.java) == true) {
                        fetcher.second.invoke(fetcher.first, values, env)
                    } else {
                        fetcher?.second?.invoke(fetcher.first, values)
                    }

                    if (result is CompletableFuture<*>) {
                        result
                    } else {
                        CompletableFuture.completedFuture(result)
                    }
                }.filter { it != null }
                .collect(Collectors.toList())

        return CompletableFuture.allOf(*resultList.toTypedArray()).thenApply {
            resultList.stream()
                    .map { cf -> cf.join() }
                    .flatMap { r -> if(r is Collection<*>) r.stream() else Stream.of(r) }
                    .collect(Collectors.toList())
        }
    }

    open fun typeMapping(): Map<Class<*>, String> {
        return emptyMap()
    }

    override fun typeResolver(): TypeResolver {
        return TypeResolver { env ->
            val src: Any = env.getObject()

            val typeName = if (typeMapping().containsKey(src::class.java)) typeMapping()[src::class.java] else src::class.java.simpleName
            val type = env.schema.getObjectType(typeName)
            if (type == null) {
                logger.warn("No type definition found for {}. You probably need to provide either a type mapping, or override DefaultDgsFederationResolver.typeResolver(). Alternatively make sure the type name in the schema and your Java model match", src::class.java.name)
            }

            type
        }
    }
}