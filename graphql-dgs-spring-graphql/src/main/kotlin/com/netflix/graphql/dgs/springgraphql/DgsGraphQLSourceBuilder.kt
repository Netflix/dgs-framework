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

import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import graphql.GraphQL
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchema.BuilderWithoutTypes
import graphql.schema.GraphQLTypeVisitor
import graphql.schema.SchemaTransformer
import graphql.schema.SchemaTraverser
import graphql.schema.TypeResolver
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeDefinitionRegistry
import org.springframework.core.io.Resource
import org.springframework.graphql.execution.ContextDataFetcherDecorator
import org.springframework.graphql.execution.DataFetcherExceptionResolver
import org.springframework.graphql.execution.GraphQlSource
import org.springframework.graphql.execution.GraphQlSource.SchemaResourceBuilder
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import org.springframework.graphql.execution.SchemaReport
import org.springframework.graphql.execution.SubscriptionExceptionResolver
import org.springframework.graphql.execution.TypeDefinitionConfigurer
import org.springframework.graphql.execution.TypeVisitorHelper
import org.springframework.lang.Nullable
import java.util.function.BiFunction
import java.util.function.Consumer

class DgsGraphQLSourceBuilder(private val dgsSchemaProvider: DgsSchemaProvider) : SchemaResourceBuilder {
    private val typeDefinitionConfigurers = mutableListOf<TypeDefinitionConfigurer>()
    private val runtimeWiringConfigurers = mutableListOf<RuntimeWiringConfigurer>()

    private val exceptionResolvers = mutableListOf<DataFetcherExceptionResolver>()
    private val subscriptionExceptionResolvers = mutableListOf<SubscriptionExceptionResolver>()
    private val typeVisitors = mutableListOf<GraphQLTypeVisitor>()
    private val typeVisitorsToTransformSchema = mutableListOf<GraphQLTypeVisitor>()
    private val instrumentations = mutableListOf<Instrumentation>()
    private var graphQlConfigurers = Consumer { builder: GraphQL.Builder -> }

    @Nullable
    private var typeResolver: TypeResolver? = null

    @Nullable
    private var schemaReportConsumer: Consumer<SchemaReport>? = null

    override fun exceptionResolvers(resolvers: List<DataFetcherExceptionResolver>): SchemaResourceBuilder {
        exceptionResolvers.addAll(resolvers)
        return this
    }

    override fun subscriptionExceptionResolvers(resolvers: List<SubscriptionExceptionResolver>): SchemaResourceBuilder {
        subscriptionExceptionResolvers.addAll(resolvers)
        return this
    }

    override fun typeVisitors(typeVisitors: List<GraphQLTypeVisitor>): SchemaResourceBuilder {
        this.typeVisitors.addAll(typeVisitors)
        return this
    }

    override fun typeVisitorsToTransformSchema(typeVisitors: List<GraphQLTypeVisitor>): SchemaResourceBuilder {
        typeVisitorsToTransformSchema.addAll(typeVisitors)
        return this
    }

    override fun instrumentation(instrumentations: List<Instrumentation>): SchemaResourceBuilder {
        this.instrumentations.addAll(instrumentations)
        return this
    }

    override fun configureGraphQl(configurer: Consumer<GraphQL.Builder>): SchemaResourceBuilder {
        graphQlConfigurers = this.graphQlConfigurers.andThen(configurer)
        return this
    }

    override fun build(): GraphQlSource {
        var schema: GraphQLSchema = dgsSchemaProvider.schema()
        val schemaTransformer = SchemaTransformer()
        typeVisitorsToTransformSchema.forEach {
            schemaTransformer.transform(schema, it)
        }

        schema = this.applyTypeVisitors(schema)
        var builder = GraphQL.newGraphQL(schema)
        builder.defaultDataFetcherExceptionHandler(DataFetcherExceptionResolver.createExceptionHandler(this.exceptionResolvers))
        if (!instrumentations.isEmpty()) {
            builder = builder.instrumentation(ChainedInstrumentation(this.instrumentations))
        }

        graphQlConfigurers.accept(builder)

        return object : GraphQlSource {
            val graphql = builder.build()

            override fun graphQl(): GraphQL {
                return graphql
            }

            override fun schema(): GraphQLSchema {
                return schema
            }
        }
    }

    private fun applyTypeVisitors(schema: GraphQLSchema): GraphQLSchema {
        val outputCodeRegistry =
            GraphQLCodeRegistry.newCodeRegistry(schema.codeRegistry)

        val vars: MutableMap<Class<*>, Any> = HashMap(2)
        vars[GraphQLCodeRegistry.Builder::class.java] = outputCodeRegistry
        vars[TypeVisitorHelper::class.java] = TypeVisitorHelper.create(schema)

        val visitorsToUse: MutableList<GraphQLTypeVisitor> = ArrayList(this.typeVisitors)
        visitorsToUse.add(ContextDataFetcherDecorator.createVisitor(this.subscriptionExceptionResolvers))

        SchemaTraverser().depthFirstFullSchema(visitorsToUse, schema, vars)
        return schema.transformWithoutTypes { builder: BuilderWithoutTypes ->
            builder.codeRegistry(
                outputCodeRegistry
            )
        }
    }

    override fun schemaResources(vararg resources: Resource?): SchemaResourceBuilder {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }
}
