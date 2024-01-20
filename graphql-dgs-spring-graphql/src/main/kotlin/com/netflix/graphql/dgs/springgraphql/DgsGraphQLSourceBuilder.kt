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

import com.netflix.graphql.dgs.internal.DefaultDgsQueryExecutor
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import graphql.schema.GraphQLSchema
import graphql.schema.TypeResolver
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeDefinitionRegistry
import org.springframework.core.io.Resource
import org.springframework.graphql.execution.*
import org.springframework.graphql.execution.GraphQlSource.SchemaResourceBuilder
import org.springframework.lang.Nullable
import java.util.function.BiFunction
import java.util.function.Consumer

class DgsGraphQLSourceBuilder(private val dgsSchemaProvider: DgsSchemaProvider, private val reloadSchemaIndicator: DefaultDgsQueryExecutor.ReloadSchemaIndicator) : AbstractGraphQlSourceBuilder<SchemaResourceBuilder>(), SchemaResourceBuilder {
    private val typeDefinitionConfigurers = mutableListOf<TypeDefinitionConfigurer>()
    private val runtimeWiringConfigurers = mutableListOf<RuntimeWiringConfigurer>()

    private val schemaResources: Set<Resource> = LinkedHashSet()

    @Nullable
    private var typeResolver: TypeResolver? = null

    @Nullable
    private var schemaReportConsumer: Consumer<SchemaReport>? = null

    override fun initGraphQlSchema(): GraphQLSchema {
        var schema: GraphQLSchema = dgsSchemaProvider.schema(schemaResources = schemaResources)

        // SchemaMappingInspector needs RuntimeWiring, but cannot run here since type
        // visitors may transform the schema, for example to add Connection types.
        // TODO: refactor schemaprovider.schema to return the pair<GraphQlSchema, RuntimeWiring>
        /*if (schemaReportConsumer != null) {
            configureGraphQl { builder: GraphQL.Builder ->
                val schema = builder.build().graphQLSchema
                val report = SchemaMappingInspector.inspect(schema, runtimeWiring)
                schemaReportConsumer!!.accept(report)
            }
        }*/

        return schema
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
}
