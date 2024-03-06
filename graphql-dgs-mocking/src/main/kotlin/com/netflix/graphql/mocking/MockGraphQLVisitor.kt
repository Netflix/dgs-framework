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

package com.netflix.graphql.mocking

import graphql.Scalars
import graphql.introspection.Introspection
import graphql.schema.*
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import net.datafaker.Faker
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MockGraphQLVisitor(private val mockConfig: Map<String, Any?>, private val mockFetchers: MutableMap<FieldCoordinates, DataFetcher<*>>) : GraphQLTypeVisitorStub() {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(MockGraphQLVisitor::class.java)
    }
    private val additionalObjectTypes = mutableSetOf<GraphQLObjectType>()
    private val providedRoots: MutableList<String> = mutableListOf()
    private val faker = Faker()

    override fun visitGraphQLFieldDefinition(node: GraphQLFieldDefinition, context: TraverserContext<GraphQLSchemaElement>): TraversalControl {
        val parentNode = context.parentNode as GraphQLFieldsContainer
        if (Introspection.isIntrospectionTypes(parentNode)) {
            return TraversalControl.CONTINUE
        }

        val pathForNode = getPathForNode(context.parentNodes, node)

        if (parentNode in additionalObjectTypes || (mockConfig.keys.any { pathForNode.startsWith(it) } && !providedRoots.any { pathForNode.startsWith(it) && pathForNode.count { c -> c == '.' } != it.count { c -> c == '.' } })) {
            if (parentNode is GraphQLInterfaceType) {
                val schema = context.getVarFromParents(GraphQLSchema::class.java)
                for (objType in schema.getImplementations(parentNode)) {
                    if (objType !in context.visitedNodes()) {
                        additionalObjectTypes += objType
                    }
                }
                return TraversalControl.CONTINUE
            }

            val type = GraphQLTypeUtil.unwrapNonNull(node.type)

            val dataFetcher: DataFetcher<*> = if (mockConfig[pathForNode] != null) {
                logger.info("Returning provided mock data for {}", pathForNode)
                providedRoots += pathForNode
                getProvidedMockData(pathForNode)
            } else {
                logger.info("Generating mock data for {}", pathForNode)
                when (type) {
                    is GraphQLScalarType -> {
                        StaticDataFetcher(generateDataForScalar(type))
                    }
                    is GraphQLEnumType -> {
                        StaticDataFetcher(type.values.random().value)
                    }
                    is GraphQLList -> {
                        val elementType = GraphQLTypeUtil.unwrapNonNull(type.wrappedType)
                        if (elementType !is GraphQLNamedType) {
                            return TraversalControl.CONTINUE
                        }
                        val mockedValues: Collection<Any?> = when (elementType) {
                            is GraphQLScalarType -> (0..faker.number().numberBetween(0, 10))
                                .map { generateDataForScalar(elementType) }
                            is GraphQLEnumType -> (0..faker.number().numberBetween(0, 3))
                                .asSequence()
                                .map { elementType.values.random().name }
                                .toSet()
                            else -> (0..faker.number().numberBetween(0, 10)).map { dummyObject(elementType) }
                        }
                        StaticDataFetcher(mockedValues)
                    }
                    else -> StaticDataFetcher(dummyObject(type))
                }
            }

            mockFetchers[FieldCoordinates.coordinates(parentNode, node)] = dataFetcher
        }

        return TraversalControl.CONTINUE
    }

    private fun dummyObject(type: GraphQLType): Any {
        val displayName = GraphQLTypeUtil.simplePrint(type)
        return object {
            override fun toString(): String {
                return "DummyObject{type=$displayName}"
            }
        }
    }

    private fun generateDataForScalar(scalarType: GraphQLScalarType): Any {
        return when (scalarType) {
            Scalars.GraphQLString -> faker.book().title()
            Scalars.GraphQLBoolean -> faker.bool().bool()
            Scalars.GraphQLInt -> faker.number().randomDigit()
            Scalars.GraphQLFloat -> faker.number().randomDouble(2, 0, 100000)
            Scalars.GraphQLID -> faker.number().digit()
            else -> return dummyObject(scalarType)
        }
    }

    private fun getProvidedMockData(pathForNode: String?): DataFetcher<*> {
        return when (val provided = mockConfig[pathForNode]) {
            is DataFetcher<*> -> provided
            else -> StaticDataFetcher(provided)
        }
    }

    private fun getPathForNode(parents: List<GraphQLSchemaElement>, node: GraphQLFieldDefinition): String {
        return (parents.asReversed().asSequence().filterIsInstance<GraphQLFieldDefinition>() + sequenceOf(node))
            .map { it.name }
            .joinToString(".")
    }
}
