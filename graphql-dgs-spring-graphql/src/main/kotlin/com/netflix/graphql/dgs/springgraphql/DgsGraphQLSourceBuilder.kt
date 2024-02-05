/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.graphql.dgs.springgraphql

import com.netflix.graphql.dgs.internal.DataFetcherReference
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import graphql.schema.*
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeDefinitionRegistry
import org.springframework.core.ResolvableType
import org.springframework.core.io.Resource
import org.springframework.graphql.execution.*
import org.springframework.graphql.execution.GraphQlSource.SchemaResourceBuilder
import org.springframework.lang.Nullable
import java.util.function.BiFunction
import java.util.function.Consumer

class DgsGraphQLSourceBuilder(private val dgsSchemaProvider: DgsSchemaProvider) : AbstractGraphQlSourceBuilder<SchemaResourceBuilder>(), SchemaResourceBuilder {
    private val typeDefinitionConfigurers = mutableListOf<TypeDefinitionConfigurer>()
    private val runtimeWiringConfigurers = mutableListOf<RuntimeWiringConfigurer>()

    private val schemaResources: Set<Resource> = LinkedHashSet()

    @Nullable
    private var typeResolver: TypeResolver? = null

    @Nullable
    private var schemaReportConsumer: Consumer<SchemaReport>? = null

    override fun initGraphQlSchema(): GraphQLSchema {
        val schema = dgsSchemaProvider.schema(schemaResources = schemaResources)

        val selfDescribingDataFetchers = wrapDataFetchers(dgsSchemaProvider.resolvedDataFetchers())
        val mergedDataFetcherMap = mutableMapOf<String, Map<String, DataFetcher<Any>>>()
        mergedDataFetcherMap.putAll(selfDescribingDataFetchers)

        // merge the spring data fetcher map with dgs data fetchers
        val springDataFetchers = schema.runtimeWiring.dataFetchers
        springDataFetchers.keys.forEach {
            if (selfDescribingDataFetchers.containsKey(it)) {
                mergedDataFetcherMap[it] = (selfDescribingDataFetchers[it]!! + (springDataFetchers[it] as Map<String, DataFetcher<Any>>))
            } else {
                mergedDataFetcherMap[it] = springDataFetchers[it] as Map<String, DataFetcher<Any>>
            }
        }
        if (schemaReportConsumer != null) {
            configureGraphQl {
                val report = SchemaMappingInspector.inspect(schema.graphQLSchema, mergedDataFetcherMap)
                schemaReportConsumer!!.accept(report)
            }
        }

        return schema.graphQLSchema
    }

    override fun schemaResources(vararg resources: Resource?): SchemaResourceBuilder {
        schemaResources.plus(listOf(*resources))
        return this
    }

    override fun configureTypeDefinitions(configurer: TypeDefinitionConfigurer): SchemaResourceBuilder {
        this.typeDefinitionConfigurers.add(configurer)
        return this
    }

    override fun configureRuntimeWiring(configurer: RuntimeWiringConfigurer): SchemaResourceBuilder {
        this.runtimeWiringConfigurers.add(configurer)
        return this
    }

    override fun defaultTypeResolver(typeResolver: TypeResolver): SchemaResourceBuilder {
        this.typeResolver = typeResolver
        return this
    }

    override fun inspectSchemaMappings(reportConsumer: Consumer<SchemaReport>): SchemaResourceBuilder {
        this.schemaReportConsumer = reportConsumer
        return this
    }

    override fun schemaFactory(schemaFactory: BiFunction<TypeDefinitionRegistry, RuntimeWiring, GraphQLSchema>): SchemaResourceBuilder {
        throw IllegalStateException("Overriding the schema factory is not supported in this builder")
    }

    class SpringGraphQlDataFetcher(private val dataFetcher: DataFetcherReference) : SelfDescribingDataFetcher<Any> {
        override fun get(environment: DataFetchingEnvironment?): Any {
            TODO("Not yet implemented")
        }
        override fun getDescription(): String {
            return dataFetcher.field
        }
        override fun getReturnType(): ResolvableType {
            return ResolvableType.forMethodReturnType(dataFetcher.method)
        }
    }

    private fun wrapDataFetchers(dataFetchers: List<DataFetcherReference>): Map<String, Map<String, SelfDescribingDataFetcher<Any>>> {
        val wrappedDataFetchers: MutableMap<String, MutableMap<String, SelfDescribingDataFetcher<Any>>> = mutableMapOf()
        dataFetchers.forEach {
            val wrappedDataFetcher = SpringGraphQlDataFetcher(it)
            if (!wrappedDataFetchers.containsKey(it.parentType)) {
                wrappedDataFetchers[it.parentType] = mutableMapOf()
            }
            wrappedDataFetchers[it.parentType]!![it.field] = wrappedDataFetcher
        }
        return wrappedDataFetchers
    }
}
