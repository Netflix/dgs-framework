package com.netflix.graphql.mocking

import graphql.schema.*
import java.util.HashMap

class DgsSchemaTransformer {

    fun transformSchemaWithMockProviders(schema: GraphQLSchema, mockProviders: Set<MockProvider>): GraphQLSchema {

        val mockConfig = HashMap<String, Any>()

        mockProviders.forEach { p -> mockConfig.putAll(p.provide()) }

        return transformSchema(schema, mockConfig)
    }

    fun transformSchema(schema: GraphQLSchema, mockConfig: Map<String, *>): GraphQLSchema {
        val mockFetchers = HashMap<FieldCoordinates, DataFetcher<*>>()
        val graphQLTypeVisitorStub = MockGraphQLVisitor(mockConfig, mockFetchers)
        SchemaTraverser().depthFirst(graphQLTypeVisitorStub, schema.getType("Query"))

        return schema.transform { b ->
            val newCodeRegistry = GraphQLCodeRegistry.newCodeRegistry(schema.codeRegistry)
            mockFetchers.forEach { (coordinates, dataFetcher) -> newCodeRegistry.dataFetcher(coordinates, dataFetcher) }

            b.codeRegistry(newCodeRegistry
                    .build())
        }
    }
}