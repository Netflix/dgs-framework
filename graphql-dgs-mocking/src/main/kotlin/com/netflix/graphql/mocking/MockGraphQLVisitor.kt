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

import graphql.schema.*
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import net.datafaker.Faker
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MockGraphQLVisitor(private val mockConfig: Map<String, Any?>, private val mockFetchers: MutableMap<FieldCoordinates, DataFetcher<*>>) : GraphQLTypeVisitorStub() {
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)
    private val providedRoots: MutableList<String?> = mutableListOf()
    private val faker = Faker()

    override fun visitGraphQLFieldDefinition(node: GraphQLFieldDefinition?, context: TraverserContext<GraphQLSchemaElement>?): TraversalControl {
        val pathForNode = getPathForNode(context?.parentNodes, node)

        if (mockConfig.keys.any { pathForNode?.startsWith(it) == true } && !providedRoots.any { pathForNode?.startsWith(it!!) == true && pathForNode.count { c -> c == '.' } != it?.count { c -> c == '.' } }) {
            val type = if (node?.type is GraphQLNonNull) (node.type as GraphQLNonNull).wrappedType else node?.type

            val dataFetcher: DataFetcher<*> = if (mockConfig[pathForNode] != null) {
                logger.info("Returning provided mock data for {}", pathForNode)
                providedRoots.add(pathForNode)
                getProvidedMockData(pathForNode)
            } else {
                logger.info("Generating mock data for {}", pathForNode)
                when (type) {
                    is GraphQLScalarType -> {
                        DataFetcher { generateDataForScalar(type.name) }
                    }
                    is GraphQLList -> {
                        val wrappedType = when (type.wrappedType) {
                            is GraphQLNamedType -> (type.wrappedType as GraphQLNamedType).name
                            else -> return super.visitGraphQLFieldDefinition(node, context)
                        }

                        val mockedValues = (0..faker.number().numberBetween(0, 10))
                            .map { generateDataForScalar(wrappedType) }
                            .toList()
                        DataFetcher { mockedValues }
                    }
                    else -> DataFetcher { Object() }
                }
            }

            mockFetchers[FieldCoordinates.coordinates((context?.parentNode as GraphQLObjectType).name, node?.name)] = dataFetcher
        }

        return super.visitGraphQLFieldDefinition(node, context)
    }

    private fun generateDataForScalar(type: String): Any {
        return when (type) {
            "String" -> faker.book().title()
            "Boolean" -> faker.bool().bool()
            "Int" -> faker.number().randomDigit()
            "Float" -> faker.number().randomDouble(2, 0, 100000)
            "ID" -> faker.number().digit()
            else -> Object()
        }
    }

    private fun getProvidedMockData(pathForNode: String?): DataFetcher<*> {
        return when (val provided = mockConfig[pathForNode]) {
            is DataFetcher<*> -> provided
            else -> DataFetcher { provided }
        }
    }

    private val getPathForNode = { parents: List<GraphQLSchemaElement>?, node: GraphQLFieldDefinition? -> parents?.filterIsInstance<GraphQLFieldDefinition>()?.map { it.name }?.fold(node?.name) { n, result -> "$result.$n" } }
}
