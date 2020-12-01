package com.netflix.graphql.mocking

import com.github.javafaker.Faker
import graphql.schema.*
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MockGraphQLVisitor(private val mockConfig: Map<String, Any?>, private val mockFetchers: MutableMap<FieldCoordinates, DataFetcher<*>>) : GraphQLTypeVisitorStub() {
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)
    private val providedRoots : MutableList<String?> = mutableListOf()

    override fun visitGraphQLFieldDefinition(node: GraphQLFieldDefinition?, context: TraverserContext<GraphQLSchemaElement>?): TraversalControl {
        val pathForNode = getPathForNode(context?.parentNodes, node)

        if(mockConfig.keys.any { pathForNode?.startsWith(it) == true } && !providedRoots.any { pathForNode?.startsWith(it!!) == true && pathForNode.count { c -> c == '.' }  != it?.count { c -> c == '.'} } ) {
            val type = if(node?.type is GraphQLNonNull) (node.type as GraphQLNonNull).wrappedType else node?.type

            val dataFetcher : DataFetcher<*> = if(mockConfig[pathForNode] != null) {
                logger.info("Returning provided mock data for {}", pathForNode)
                providedRoots.add(pathForNode)
                getProvidedMockData(pathForNode)
            } else {
                logger.info("Generating mock data for {}", pathForNode)
                when(type) {
                    is GraphQLScalarType -> {
                        DataFetcher { generateDataForScalar(type.name) }
                    }
                    is GraphQLList -> {
                        val wrappedType = when(type.wrappedType) {
                            is GraphQLNamedType -> (type.wrappedType as GraphQLNamedType).name
                            else -> return super.visitGraphQLFieldDefinition(node, context)
                        }

                        val mockedValues = (0..Faker().number().numberBetween(0,10))
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
        return when(type) {
            "String" -> Faker().book().title()
            "Boolean" -> Faker().bool().bool()
            "Int" -> Faker().number().randomDigit()
            "Float" ->  Faker().number().randomDouble(2, 0, 100000)
            "ID" -> Faker().number().digit()
            else -> Object()
        }
    }

    private fun getProvidedMockData(pathForNode: String?): DataFetcher<*> {

        return when(val provided = mockConfig[pathForNode]) {
            is DataFetcher<*> -> provided
            else -> DataFetcher { provided }
        }
    }

    private val getPathForNode = {parents: List<GraphQLSchemaElement>?, node: GraphQLFieldDefinition? -> parents?.filterIsInstance<GraphQLFieldDefinition>()?.map {it.name}?.fold(node?.name) { n, result -> "$result.$n" }}


}