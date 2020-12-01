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

package com.netflix.graphql.mocking

import com.netflix.graphql.mocking.testobjects.MyObject
import com.netflix.graphql.mocking.testobjects.SomeObject
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.*
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class MockGraphQLVisitorTest {

    @Test
    fun generateDataForStringScalar() {
        val mockConfig = mapOf(Pair("someObject.someKey", null))

        val schema = createSchema("""
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: String
            }
        """)


        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        Assertions.assertTrue((someObject["someKey"] as String).isNotEmpty())
    }

    @Test
    fun generateDataForBooleanScalar() {
        val mockConfig = mapOf(Pair("someObject.someKey", null))

        val schema = createSchema("""
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: Boolean
            }
        """)


        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        Assertions.assertEquals(Boolean::class, someObject["someKey"]!!::class)
    }

    @Test
    fun generateDataForIntScalar() {
        val mockConfig = mapOf(Pair("someObject.someKey", null))

        val schema = createSchema("""
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: Int
            }
        """)


        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        Assertions.assertEquals(Int::class, someObject["someKey"]!!::class)
    }

    @Test
    fun generateDataForFloatScalar() {
        val mockConfig = mapOf(Pair("someObject.someKey", null))

        val schema = createSchema("""
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: Float
            }
        """)


        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        Assertions.assertEquals(Double::class, someObject["someKey"]!!::class)
    }

    @Test
    fun generateDataForIDScalar() {
        val mockConfig = mapOf(Pair("someObject.someKey", null))

        val schema = createSchema("""
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: ID
            }
        """)


        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        Assertions.assertEquals(String::class, someObject["someKey"]!!::class)
    }

    @Test
    fun generateDataForNonNullableString() {
        val mockConfig = mapOf(Pair("someObject.someKey", null))

        val schema = createSchema("""
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: String!
            }
        """)


        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        Assertions.assertEquals(String::class, someObject["someKey"]!!::class)
    }

    @Test
    fun generateDataForStringList() {
        val mockConfig = mapOf(Pair("someObject.someKey", null))

        val schema = createSchema("""
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: [String]
            }
        """)


        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>

        when(val value = someObject["someKey"]!!) {
            is List<*> -> value.forEach { Assertions.assertTrue(it is String)}
            else -> Assertions.fail("Returned mock is not a List")
        }
    }

    @Test
    fun generateDataForObject() {
        val mockConfig = mapOf(Pair("someObject.someKey", null))

        val schema = createSchema("""
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: MyObject
            }
            
            type MyObject {
                name: String
            }
        """)


        val data = execute(schema, "query { someObject {someKey { name} } }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        val someKey = someObject["someKey"] as Map<*,*>
        Assertions.assertNotNull(someKey)
        Assertions.assertTrue(someKey["name"] is String)
    }

    @Test
    fun generateDataForObjectList() {
        val mockConfig = mapOf(Pair("someObject.someKey", null))

        val schema = createSchema("""
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: [MyObject]
            }
            
            type MyObject {
                name: String
            }
        """)


        val data = execute(schema, "query { someObject {someKey { name} } }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        val myObjectArr = someObject["someKey"] as List<*>
        Assertions.assertTrue(((myObjectArr[0] as Map<*,*>)["name"] as String).isNotBlank())

    }

    @Test
    fun providedMockData() {
        val mockConfig = mapOf(Pair("someObject.someKey", listOf("a", "b", "c")))

        val schema = createSchema("""
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: [String]
            }
        """)


        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        Assertions.assertEquals(listOf("a", "b", "c"), someObject["someKey"])
    }

    @Test
    fun providedMockDataForObject() {
        val mockConfig = mapOf(Pair("someObject.someKey", listOf(MyObject(name = "mymockedvalue"))))

        val schema = createSchema("""
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: [MyObject]
            }
            
            type MyObject {
                name: String
            }
        """)

        val data = execute(schema, "query { someObject {someKey { name} } }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        val someKeyList = someObject["someKey"] as List<*>
        Assertions.assertNotNull(someKeyList)
        Assertions.assertEquals(1, someKeyList.size)
        Assertions.assertEquals("mymockedvalue", (someKeyList[0] as Map<*,*>)["name"])
    }

    @Test
    fun providedMockDataFetcherData() {
        val dataFetcher = DataFetcher { listOf("a", "b", "c") }
        val mockConfig = mapOf(Pair("someObject.someKey", dataFetcher))

        val schema = createSchema("""
            type Query {
               someObject: SomeObject
            }

            type SomeObject {
                someKey: [String]
            }
        """)


        val data = execute(schema, "query { someObject {someKey} }", mockConfig)

        val someObject = data["someObject"] as Map<*, *>
        Assertions.assertEquals(listOf("a", "b", "c"), someObject["someKey"])
    }

    @Test
    fun multipleMocksSimilarName() {
        val nameFetcher = DataFetcher { "nameMock" }
        val namesFetcher = DataFetcher { listOf("listNameMock") }
        val mockConfig = mapOf(Pair("name", nameFetcher), Pair("names", namesFetcher))

        val schema = createSchema("""
            type Query {
               name: String
               names: [String]
            }
        """)


        val data = execute(schema, "query { names }", mockConfig)

        val names = data["names"] as List<*>
        Assertions.assertEquals(listOf("listNameMock"), names)
    }

  private fun execute(schema: GraphQLSchema, query: String, mockConfig: Map<String, Any?>): Map<String, *> {
        val transform = DgsSchemaTransformer().transformSchema(schema, mockConfig)

        val graphQL = GraphQL.newGraphQL(transform)
                .build()

        val executionInput = ExecutionInput.newExecutionInput().query(query)
                .build()

        val executionResult = graphQL.execute(executionInput)
        return executionResult.getData()
    }

    private fun createSchema(schema : String): GraphQLSchema {
        val schemaParser = SchemaParser()
        val typeDefinitionRegistry = schemaParser.parse(schema.trimIndent())

        val someObjectDf = DataFetcher { SomeObject() }

        val codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(FieldCoordinates.coordinates("Query", "someObject"), someObjectDf)

        val runtimeWiring = RuntimeWiring.newRuntimeWiring().codeRegistry(codeRegistry).build()

        val schemaGenerator = SchemaGenerator()
        return schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)
    }


}