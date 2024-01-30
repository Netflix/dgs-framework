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

import com.netflix.graphql.mocking.testobjects.MyObject
import com.netflix.graphql.mocking.testobjects.SomeObject
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLSchema
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeRuntimeWiring
import org.assertj.core.api.Assertions.LIST
import org.assertj.core.api.Assertions.MAP
import org.assertj.core.api.Assertions.STRING
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MockGraphQLVisitorTest {

    @Test
    fun generateDataForStringScalar() {
        val mockConfig = mapOf("someObject.someKey" to null)

        val schema = createSchema(
            """
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: String
            }
        """
        )

        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        assertThat(someObject["someKey"]).asString().isNotEmpty()
    }

    @Test
    fun generateDataForBooleanScalar() {
        val mockConfig = mapOf("someObject.someKey" to null)

        val schema = createSchema(
            """
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: Boolean
            }
        """
        )

        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        assertThat(someObject["someKey"]).isInstanceOf(Boolean::class.javaObjectType)
    }

    @Test
    fun generateDataForIntScalar() {
        val mockConfig = mapOf("someObject.someKey" to null)

        val schema = createSchema(
            """
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: Int
            }
        """
        )

        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        assertThat(someObject["someKey"]).isInstanceOf(Int::class.javaObjectType)
    }

    @Test
    fun generateDataForFloatScalar() {
        val mockConfig = mapOf("someObject.someKey" to null)

        val schema = createSchema(
            """
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: Float
            }
        """
        )

        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        assertThat(someObject["someKey"]).isInstanceOf(Double::class.javaObjectType)
    }

    @Test
    fun generateDataForIDScalar() {
        val mockConfig = mapOf("someObject.someKey" to null)

        val schema = createSchema(
            """
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: ID
            }
        """
        )

        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        assertThat(someObject["someKey"]).asInstanceOf(STRING).isNotBlank()
    }

    @Test
    fun generateDataForNonNullableString() {
        val mockConfig = mapOf("someObject.someKey" to null)

        val schema = createSchema(
            """
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: String!
            }
        """
        )

        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        assertThat(someObject["someKey"]).asInstanceOf(STRING).isNotBlank()
    }

    @Test
    fun generateDataForStringList() {
        val mockConfig = mapOf("someObject.someKey" to null)

        val schema = createSchema(
            """
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: [String]
            }
        """
        )

        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>

        when (val value = someObject["someKey"]!!) {
            is List<*> -> value.forEach { assertThat(it).isInstanceOf(String::class.java) }
            else -> Assertions.fail("Returned mock is not a List")
        }
    }

    @Test
    fun generateDataForObject() {
        val mockConfig = mapOf("someObject.someKey" to null)

        val schema = createSchema(
            """
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: MyObject
            }
            
            type MyObject {
                name: String
            }
        """
        )

        val data = execute(schema, "query { someObject {someKey { name} } }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        val someKey = someObject["someKey"] as Map<*, *>
        assertThat(someKey["name"]).asInstanceOf(STRING).isNotBlank()
    }

    @Test
    fun generateDataForObjectList() {
        val mockConfig = mapOf("someObject.someKey" to null)

        val schema = createSchema(
            """
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: [MyObject]
            }
            
            type MyObject {
                name: String
            }
        """
        )

        val data = execute(schema, "query { someObject {someKey { name} } }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        val myObjectArr = someObject["someKey"] as List<*>
        assertThat(myObjectArr[0]).asInstanceOf(MAP)
            .extractingByKey("name")
            .asInstanceOf(STRING)
            .isNotBlank()
    }

    @Test
    fun providedMockData() {
        val mockConfig = mapOf("someObject.someKey" to listOf("a", "b", "c"))

        val schema = createSchema(
            """
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: [String]
            }
        """
        )

        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        assertThat(someObject["someKey"]).asInstanceOf(LIST)
            .containsExactly("a", "b", "c")
    }

    @Test
    fun providedMockDataForObject() {
        val mockConfig = mapOf("someObject.someKey" to listOf(MyObject(name = "mymockedvalue")))

        val schema = createSchema(
            """
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: [MyObject]
            }
            
            type MyObject {
                name: String
            }
        """
        )

        val data = execute(schema, "query { someObject {someKey { name} } }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        val someKeyList = someObject["someKey"] as List<*>
        assertThat(someKeyList).size().isEqualTo(1)
        assertThat(someKeyList[0]).asInstanceOf(MAP).extractingByKey("name").isEqualTo("mymockedvalue")
    }

    @Test
    fun providedMockDataFetcherData() {
        val dataFetcher = DataFetcher { listOf("a", "b", "c") }
        val mockConfig = mapOf("someObject.someKey" to dataFetcher)

        val schema = createSchema(
            """
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: [String]
            }
        """
        )

        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        assertThat(someObject["someKey"]).asInstanceOf(LIST).containsExactly("a", "b", "c")
    }

    @Test
    fun multipleMocksSimilarName() {
        val nameFetcher = StaticDataFetcher("nameMock")
        val namesFetcher = StaticDataFetcher(listOf("listNameMock"))
        val mockConfig = mapOf("name" to nameFetcher, "names" to namesFetcher)

        val schema = createSchema(
            """
            type Query {
               name: String
               names: [String]
            }
        """
        )

        val data = execute(schema, "query { names }", mockConfig)

        val names = data["names"] as List<*>
        assertThat(names).containsExactly("listNameMock")
    }

    @Test
    fun generateDataForEnumType() {
        val schema = """
            type Query {
               animalKind: AnimalKind!
            }

            enum AnimalKind { DOG, SLOTH, BEAR, PIG }
        """

        val graphqlSchema = createSchema(schema)

        val data = execute(graphqlSchema, "query { animalKind }", mapOf("animalKind" to null))
        val animalKind = data["animalKind"] as String
        assertThat(animalKind).isIn("DOG", "SLOTH", "BEAR", "PIG")
    }

    @Test
    fun generateDataForListOfEnumType() {
        val schema = """
            type Query {
               animalKinds: [AnimalKind!]!
            }

            enum AnimalKind { DOG, SLOTH, BEAR, PIG }
        """

        val graphqlSchema = createSchema(schema)

        val data = execute(graphqlSchema, "query { animalKinds }", mapOf("animalKinds" to null))
        val animalKind = data["animalKinds"] as List<*>
        assertThat(animalKind).isSubsetOf("DOG", "SLOTH", "BEAR", "PIG")
    }

    @Test
    fun generateDataForUnionType() {
        val schema = """
            type Query {
               animal: Animal
            }

            union Animal = Dog | Sloth

            type Dog {
              name: String
              barkVolume: Int
            }

            type Sloth {
              chill: Boolean
              speed: Int
            }
        """

        val graphqlSchema = createSchema(schema) { builder ->
            builder.type(TypeRuntimeWiring.newTypeWiring("Animal").typeResolver { env -> env.schema.getObjectType("Sloth") })
        }

        val data = execute(graphqlSchema, "query { animal { __typename ... on Sloth { chill speed } } }", mapOf("animal" to null, "Sloth" to null))
        val slothData = data["animal"] as Map<*, *>
        assertThat(slothData["chill"]).isInstanceOf(Boolean::class.javaObjectType)
        assertThat(slothData["speed"]).isInstanceOf(Int::class.javaObjectType)
    }

    @Test
    fun generateDataForInterfaceType() {
        val schema = """
            type Query {
               animal: Animal
            }

            interface Animal {
              speed: Int
            }

            type Dog implements Animal {
              name: String
              barkVolume: Int
              speed: Int
            }

            type Sloth implements Animal {
              chill: Boolean
              speed: Int
            }
        """

        val graphqlSchema = createSchema(schema) { builder ->
            builder.type(TypeRuntimeWiring.newTypeWiring("Animal").typeResolver { env -> env.schema.getObjectType("Sloth") })
        }

        val data = execute(graphqlSchema, "query { animal { __typename ... on Sloth { chill speed } } }", mapOf("animal" to null))
        val slothData = data["animal"] as Map<*, *>
        Assertions.assertInstanceOf(Boolean::class.javaObjectType, slothData["chill"])
        Assertions.assertInstanceOf(Int::class.javaObjectType, slothData["speed"])
    }

    private fun execute(schema: GraphQLSchema, query: String, mockConfig: Map<String, Any?>): Map<String, *> {
        val transform = DgsSchemaTransformer().transformSchema(schema, mockConfig)

        val graphQL = GraphQL.newGraphQL(transform)
            .build()

        val executionInput = ExecutionInput.newExecutionInput().query(query)
            .build()

        val executionResult = graphQL.execute(executionInput)
        if (!executionResult.isDataPresent) {
            throw AssertionError("GraphQL query failed: $executionResult")
        }
        return executionResult.getData()
    }

    private fun createSchema(schema: String, block: (RuntimeWiring.Builder) -> Unit = {}): GraphQLSchema {
        val schemaParser = SchemaParser()
        val typeDefinitionRegistry = schemaParser.parse(schema.trimIndent())

        val someObjectDf = StaticDataFetcher(SomeObject())

        val codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
            .dataFetcher(FieldCoordinates.coordinates("Query", "someObject"), someObjectDf)
            .build()

        val runtimeWiring = RuntimeWiring.newRuntimeWiring().codeRegistry(codeRegistry).build().transform(block)

        val schemaGenerator = SchemaGenerator()
        return schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)
    }
}
