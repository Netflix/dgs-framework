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

package com.netflix.graphql.dgs

import com.netflix.graphql.dgs.logging.internal.QuerySanitizer
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.UnExecutableSchemaGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class QuerySanitizerTest {
    private val registry: TypeDefinitionRegistry? = SchemaParser().parse("""
        type Query {
          getPerson(id: ID!): Person
        }

        type Mutation {
          updatePerson(person: UpdatePersonInput): UpdatePersonPayload
        }

        input UpdatePersonInput {
          id: ID
          name: String
          birthday: DateInput
        }

        input DateInput {
          year: Int
          month: Int
          day: Int
        }

        type UpdatePersonPayload {
          person: Person
        }

        type Person {
          id: ID
          name: String
        }
    """.trimIndent())

    val schema: GraphQLSchema = UnExecutableSchemaGenerator.makeUnExecutableSchema(registry)

    @Test
    fun testBasicInputSanitization() {
        val document = Parser().parseDocument("""
            mutation UpdateQuery {
              updatePerson(person: {id: "123", name: "blah", birthday: {year: 1234, month: 12, day: 12}}) {
                person {
                  name
                }
              }
            }
        """.trimIndent())

        val querySanitizer = QuerySanitizer(document, schema)
        val sanitized = querySanitizer.sanitized()

        assertEquals("""
            mutation UpdateQuery(${'$'}var1: UpdatePersonInput) {
              updatePerson(person: ${'$'}var1) {
                person {
                  name
                }
              }
            }
        """.trimIndent(), sanitized[0])
    }

    @Test
    fun testFragments() {
        val document = Parser().parseDocument("""
            mutation UpdateQuery {
              updatePerson(person: {id: "123", name: "blah", birthday: {year: 1234, month: 12, day: 12}}) {
                person {
                  ...someAttributes
                  ...nameAttribute
                }
              }
            }

            fragment someAttributes on Person {
              id
              name
            }

            fragment nameAttribute on Person {
              name
            }
        """.trimIndent())

        val querySanitizer = QuerySanitizer(document, schema)
        val sanitized = querySanitizer.sanitized()

        assertEquals("""
            mutation UpdateQuery(${'$'}var1: UpdatePersonInput) {
              updatePerson(person: ${'$'}var1) {
                person {
                  ... on Person {
                    id
                    name
                  }
                  ... on Person {
                    name
                  }
                }
              }
            }
        """.trimIndent(), sanitized[0])
    }

    @Test
    fun testFragmentsWithMultipleOperations() {
        val document = Parser().parseDocument("""
            mutation UpdateQuery {
              updatePerson(person: {id: "123", name: "blah", birthday: {year: 1234, month: 12, day: 12}}) {
                person {
                  ...someAttributes
                  ...nameAttribute
                }
              }
            }

            query FindPerson {
              getPerson(id: "123") {
                ...someAttributes
                ...nameAttribute
              }
            }

            fragment someAttributes on Person {
              id
              name
            }

            fragment nameAttribute on Person {
              name
            }
        """.trimIndent())

        val querySanitizer = QuerySanitizer(document, schema)
        val sanitized = querySanitizer.sanitized()

        assertEquals("""
            mutation UpdateQuery(${'$'}var1: UpdatePersonInput) {
              updatePerson(person: ${'$'}var1) {
                person {
                  ... on Person {
                    id
                    name
                  }
                  ... on Person {
                    name
                  }
                }
              }
            }
        """.trimIndent(), sanitized[0])

        assertEquals("""
            query FindPerson(${'$'}var1: ID!) {
              getPerson(id: ${'$'}var1) {
                ... on Person {
                  id
                  name
                }
                ... on Person {
                  name
                }
              }
            }
        """.trimIndent(), sanitized[1])
    }
}