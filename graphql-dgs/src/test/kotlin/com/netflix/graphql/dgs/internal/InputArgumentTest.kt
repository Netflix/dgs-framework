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

package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDirective
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.DgsRuntimeWiring
import com.netflix.graphql.dgs.DgsScalar
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.LocalDateTimeScalar
import com.netflix.graphql.dgs.internal.java.test.enums.JGreetingType
import com.netflix.graphql.dgs.internal.java.test.enums.JInputMessage
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JEnum
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JFilter
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JFooInput
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JListOfListsOfLists
import com.netflix.graphql.dgs.internal.java.test.inputobjects.JPerson
import com.netflix.graphql.dgs.internal.java.test.inputobjects.sortby.JMovieSortBy
import com.netflix.graphql.dgs.internal.kotlin.test.DateTimeInput
import com.netflix.graphql.dgs.internal.kotlin.test.KBarInput
import com.netflix.graphql.dgs.internal.kotlin.test.KEnum
import com.netflix.graphql.dgs.internal.kotlin.test.KFilter
import com.netflix.graphql.dgs.internal.kotlin.test.KFooInput
import com.netflix.graphql.dgs.internal.kotlin.test.KGreetingType
import com.netflix.graphql.dgs.internal.kotlin.test.KInputMessage
import com.netflix.graphql.dgs.internal.kotlin.test.KListOfListsOfLists
import com.netflix.graphql.dgs.internal.kotlin.test.KMovieFilter
import com.netflix.graphql.dgs.internal.kotlin.test.Person
import com.netflix.graphql.dgs.internal.method.DataFetchingEnvironmentArgumentResolver
import com.netflix.graphql.dgs.internal.method.FallbackEnvironmentArgumentResolver
import com.netflix.graphql.dgs.internal.method.InputArgumentResolver
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import com.netflix.graphql.dgs.scalars.UploadScalar
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.scalars.ExtendedScalars
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import org.springframework.core.convert.ConversionFailedException
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Suppress("unused")
@ExtendWith(MockKExtension::class)
internal class InputArgumentTest {
    @MockK
    lateinit var applicationContextMock: ApplicationContext

    val provider: DgsSchemaProvider by lazy {
        DgsSchemaProvider(
            applicationContext = applicationContextMock,
            federationResolver = Optional.empty(),
            existingTypeDefinitionRegistry = Optional.empty(),
            methodDataFetcherFactory = MethodDataFetcherFactory(
                listOf(
                    InputArgumentResolver(DefaultInputObjectMapper()),
                    DataFetchingEnvironmentArgumentResolver(),
                    FallbackEnvironmentArgumentResolver()
                )
            )
        )
    }

    @Test
    fun `@InputArgument with name specified on String argument`() {
        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("name") abc: String): String {
                return "Hello, $abc"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            "helloFetcher" to fetcher
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val schema = provider.schema()

        val build = GraphQL.newGraphQL(schema).build()
        val executionResult = build.execute("""{hello(name: "tester")}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, tester", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `@InputArgument with no name specified should work`() {
        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument name: String): String {
                return "Hello, $name"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val schema = provider.schema()

        val build = GraphQL.newGraphQL(schema).build()
        val executionResult = build.execute("""{hello(name: "tester")}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, tester", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `@InputArgument with no name specified, without matching argument, should be null`() {
        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument abc: String?): String {
                return "Hello, ${abc ?: "no name"}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            "helloFetcher" to fetcher
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val schema = provider.schema()

        val build = GraphQL.newGraphQL(schema).build()
        val executionResult = build.execute("""{hello(name: "tester")}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, no name", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `@InputArgument on an input type`() {
        val schema = """
            type Query {
                hello(person:Person): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("person") person: Person): String {
                return "Hello, ${person.name}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            "helloFetcher" to fetcher
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(person: {name: "tester"})}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, tester", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `@InputArgument with name specified as 'name'`() {
        val schema = """
            type Query {
                hello(person:Person): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument(name = "person") person: Person): String {
                return "Hello, ${person.name}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(person: {name: "tester"})}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, tester", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `@InputArgument on a list of strings`() {
        val schema = """
            type Query {
                hello(names: [String]): String
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("names") names: List<String>): String {
                return "Hello, ${names.joinToString(", ")}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            "helloFetcher" to fetcher
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(names: ["tester 1", "tester 2"])}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, tester 1, tester 2", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `@InputArgument on a Set of strings`() {
        val schema = """
            type Query {
                hello(names: [String]): String
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("names") names: Set<String>): String {
                return "Hello, ${names.joinToString(", ")}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            "helloFetcher" to fetcher
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(names: ["tester 1", "tester 2"])}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, tester 1, tester 2", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `@InputArgument on a list of input types`() {
        val schema = """
            type Query {
                hello(person:[Person]): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("person") person: List<Person>): String {
                return "Hello, ${person.joinToString(", ") { it.name }}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            "helloFetcher" to fetcher
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(person: [{name: "tester"}, {name: "tester 2"}])}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, tester, tester 2", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `@InputArgument on an optional list of integers`() {
        val expectedNumbers = listOf(1, 2, 3)

        val schema = """
            type Query {
                numbers(list: [Int]): String
            }
        """.trimIndent()

        val fetcher = object {
            @DgsQuery(field = "numbers")
            fun numbers(@InputArgument("list") listOptional: Optional<List<Int>>, dfe: DataFetchingEnvironment): String {
                assertThat(listOptional).isNotEmpty
                assertThat(listOptional.get()).containsExactlyElementsOf(expectedNumbers)
                return "Numbers are ${listOptional.map{ it.joinToString(", ") }.orElse("na")}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("numbersFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{ numbers(list: [1, 2, 3]) }""")
        assertThat(executionResult.isDataPresent).isTrue

        val data = executionResult.getData<Map<String, *>>()
        assertThat(data["numbers"]).isEqualTo("Numbers are 1, 2, 3")

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `List of scalar in a nested input type`() {
        val schema = """
            type Query {
                titles(filter: MovieFilter): String
            }
            
            input MovieFilter {
                movieIds: [Object]
            }
            
            scalar Object
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "titles")
            fun someFetcher(@InputArgument("filter") filter: KMovieFilter): String {
                return filter.movieIds.joinToString { "Title for $it" }
            }

            @DgsRuntimeWiring
            fun addScalar(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
                return builder.scalar(ExtendedScalars.Object)
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{titles(filter: {movieIds: [1, "two"]})}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Title for 1, Title for two", data["titles"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Use scalar property inside a List of complex input type`() {
        val schema = """
            type Query {
                titles(input: FooInput): String
            }
            
           input FooInput {
                bars: [BarInput!]
            }
            
            input BarInput {
                name: String!
                value: Object!
            }
            
            scalar Object
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "titles")
            fun someFetcher(@InputArgument input: KFooInput): String {
                return input.bars.joinToString { "${it.name}: ${it.value}" }
            }

            @DgsRuntimeWiring
            fun addScalar(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
                return builder.scalar(ExtendedScalars.Object)
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            "helloFetcher" to fetcher
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{titles(input: {bars: [{name: "bar 1", value: 1}, {name: "bar 2", value: "two"}]})}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("bar 1: 1, bar 2: two", data["titles"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Use scalar property inside a List of complex Java input type`() {
        val schema = """
            type Query {
                titles(input: JFooInput): String
            }
            
           input JFooInput {
                bars: [JBarInput!]
            }
            
            input JBarInput {
                name: String!
                value: Object!
            }
            
            scalar Object
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "titles")
            fun someFetcher(@InputArgument input: JFooInput): String {
                return input.bars.joinToString { "${it.name}: ${it.value}" }
            }

            @DgsRuntimeWiring
            fun addScalar(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
                return builder.scalar(ExtendedScalars.Object)
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            "helloFetcher" to fetcher
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{titles(input: {bars: [{name: "bar 1", value: 1}, {name: "bar 2", value: "two"}]})}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("bar 1: 1, bar 2: two", data["titles"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Input argument of type Set should result in a DgsInvalidInputArgumentException`() {
        val schema = """
            type Query {
                hello(person:[Person]): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("person") people: Set<Person>): String {
                return "Hello, ${people.joinToString(", ") { it.name }}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            "helloFetcher" to fetcher
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()

        val executionResult = build.execute("""{hello(person: [{name: "tester"}, {name: "tester 2"}, {name: "tester"}])}""")
        assertThat(executionResult.isDataPresent).isTrue
        assertThat(executionResult.errors.size).isEqualTo(0)

        val data = executionResult.getData<Map<String, *>>()
        assertThat(data).extracting("hello").isEqualTo("Hello, tester, tester 2")

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `A DgsInvalidInputArgumentException should be thrown when the @InputArgument collectionType doesn't match the parameter type`() {
        val schema = """
            type Query {
                hello(person:[Person]): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("person") person: List<String>): String {
                return "Hello, ${person.joinToString(", ") { it }}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()

        val executionResult = build.execute("""{hello(person: [{name: "tester"}, {name: "tester 2"}])}""")
        assertThat(executionResult.errors.size).isEqualTo(1)
        val exceptionWhileDataFetching = executionResult.errors[0] as ExceptionWhileDataFetching
        assertThat(exceptionWhileDataFetching.exception).isInstanceOf(ConversionFailedException::class.java)
        assertThat(exceptionWhileDataFetching.exception.message)
            .contains("Failed to convert from type [java.util.LinkedHashMap<?, ?>] to type [@com.netflix.graphql.dgs.InputArgument java.lang.String] for value '{name=tester}'")

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `An @InputArgument representing a complex type can be empty`() {
        val schema = """
            type Query {
                hello(person: Person): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()

        val fetcher = object {
            @DgsQuery
            fun hello(@InputArgument person: JPerson): String {
                assertThat(person).isNotNull.extracting { it.name }.isNull()
                return "Hello, $person"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()

        val executionResult = build.execute("""{hello(person: {})}""")
        assertThat(executionResult.errors).hasSize(0)

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `A null value for an input argument should not result in an error`() {
        val schema = """
            type Query {
                hello(person:Person): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("person") person: Person?): String {
                if (person == null) {
                    return "Hello, Stranger"
                }
                return "Hello, ${person.name}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            "helloFetcher" to fetcher
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, Stranger", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Multiple input arguments should be supported`() {
        val schema = """
            type Query {
                hello(person:Person, capitalize:Boolean): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(
                @InputArgument("capitalize") capitalize: Boolean,
                @InputArgument("person") person: Person
            ): String {
                return if (capitalize) {
                    "Hello, ${person.name}"
                } else {
                    "hello, ${person.name}"
                }
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            "helloFetcher" to fetcher
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(capitalize: true, person: {name: "tester"})}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, tester", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Multiple input argument in combination with a DataFetchingEnvironment argument should be supported`() {
        val schema = """
            type Query {
                hello(person:Person, capitalize:Boolean, otherArg:String): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(
                dfe: DataFetchingEnvironment,
                @InputArgument("capitalize") capitalize: Boolean,
                @InputArgument("person") person: Person
            ): String {
                val otherArg: String = dfe.getArgument("otherArg")

                val msg = if (capitalize) {
                    "Hello, ${person.name}"
                } else {
                    "hello, ${person.name}"
                }
                return msg + otherArg
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            "helloFetcher" to fetcher
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(capitalize: true, person: {name: "tester"}, otherArg: "!")}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, tester!", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Multiple input argument in combination with a DataFetchingEnvironment argument should be supported in any order`() {
        val schema = """
            type Query {
                hello(person:Person, capitalize:Boolean, otherArg:String): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(
                @InputArgument("capitalize") capitalize: Boolean,
                @InputArgument("person") person: Person,
                dfe: DataFetchingEnvironment
            ): String {
                val otherArg: String = dfe.getArgument("otherArg")

                val msg = if (capitalize) {
                    "Hello, ${person.name}"
                } else {
                    "hello, ${person.name}"
                }
                return msg + otherArg
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(capitalize: true, person: {name: "tester"}, otherArg: "!")}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, tester!", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Input arguments of type MultipartFile should be supported`() {
        val schema = """
            type Mutation {
                upload(file: Upload): String
            }

            scalar Upload
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Mutation", field = "upload")
            fun someFetcher(@InputArgument("file") file: MultipartFile): String {
                return String(file.bytes)
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            "helloFetcher" to fetcher,
            "Upload" to UploadScalar(),
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val file: MultipartFile =
            MockMultipartFile("hello.txt", "hello.txt", MediaType.TEXT_PLAIN_VALUE, "Hello World".toByteArray())

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute(
            ExecutionInput.newExecutionInput().query("mutation(\$input: Upload!)  { upload(file: \$input) }")
                .variables(mapOf("input" to file))
        )
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello World", data["upload"])
    }

    @Test
    fun `An unknown argument should be null, and not result in an error`() {
        val schema = """
            type Query {
                hello: String
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(someArg: String?): String {

                return "Hello, $someArg"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, null", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Scalars should be supported as input arguments - testing with DateTime`() {
        val schema = """
            type Mutation {
                setDate(date:DateTime): String
            }                     
            
            scalar DateTime
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Mutation", field = "setDate")
            fun someFetcher(@InputArgument("date") date: LocalDateTime): String {
                return "The date is: ${date.format(DateTimeFormatter.ISO_DATE)}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns mapOf("DateTime" to LocalDateTimeScalar())
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""mutation {setDate(date: "2021-01-27T10:15:30")}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("The date is: 2021-01-27", data["setDate"])
    }

    @Test
    fun `null value Scalars should be supported as input arguments - testing with DateTime`() {
        val schema = """
            type Mutation {
                setDate(date:DateTime): String
            }                     
            
            scalar DateTime
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Mutation", field = "setDate")
            fun someFetcher(@InputArgument("date") date: LocalDateTime?): String {
                if (date == null) {
                    return "The future is now"
                }

                return "The date is: ${date.format(DateTimeFormatter.ISO_DATE)}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns mapOf("DateTime" to LocalDateTimeScalar())
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""mutation {setDate(date: null)}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("The future is now", data["setDate"])
    }

    @Test
    fun `Lists of scalars should be supported - testing with list of DateTime`() {
        val schema = """
            type Mutation {
                setDate(date:[DateTime]): String
            }                     
            
            scalar DateTime
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Mutation", field = "setDate")
            fun someFetcher(@InputArgument("date") date: List<LocalDateTime>): String {
                return "The date is: ${date[0].format(DateTimeFormatter.ISO_DATE)}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns mapOf("DateTime" to LocalDateTimeScalar())
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""mutation {setDate(date: ["2021-01-27T10:15:30"])}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("The date is: 2021-01-27", data["setDate"])
    }

    @Test
    fun `Scalars should work even when nested in an input type`() {
        val schema = """
            type Mutation {
                setDate(input:DateTimeInput): String
            }
            
            input DateTimeInput {
                date: DateTime
            }
            
            scalar DateTime
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Mutation", field = "setDate")
            fun someFetcher(@InputArgument("input") input: DateTimeInput): String {
                return "The date is: ${input.date.format(DateTimeFormatter.ISO_DATE)}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns mapOf("DateTime" to LocalDateTimeScalar())
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""mutation {setDate(input: {date: "2021-01-27T10:15:30"})}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("The date is: 2021-01-27", data["setDate"])
    }

    @Test
    fun `Lists of primitive types should be supported`() {
        val schema = """
            type Mutation {
                setRatings(ratings:[Int!]): [Int]
            }                     
            
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Mutation", field = "setRatings")
            @Suppress("UNUSED_PARAMETER")
            fun someFetcher(@InputArgument("ratings") ratings: List<Int>): List<Int> {
                return listOf(1, 2, 3)
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""mutation {setRatings(ratings: [1, 2, 3])}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals(listOf(1, 2, 3), data["setRatings"])
    }

    @Test
    fun `An @InputArgument could be of type Optional`() {
        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument name: Optional<String>): String {
                return "Hello, ${name.orElse("default value")}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val schema = provider.schema()

        val build = GraphQL.newGraphQL(schema).build()
        val executionResult = build.execute("""{hello(name: "tester")}""")
        Assertions.assertTrue(executionResult.errors.isEmpty())
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, tester", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `An @InputArgument with a complex type could be wrapped in Optional`() {
        val schema = """
            type Query {
                hello(person:Person): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("person") person: Optional<Person>): String {
                return "Hello, ${person.get().name}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            "helloFetcher" to fetcher
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(person: {name: "tester"})}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, tester", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `An @InputArgument of type Optional receives empty by default`() {
        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument name: Optional<String>): String {
                return "Hello, ${name.orElse("default value")}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val schema = provider.schema()

        val build = GraphQL.newGraphQL(schema).build()
        val executionResult = build.execute("""{hello}""")
        Assertions.assertTrue(executionResult.errors.isEmpty())
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, default value", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Kotlin enum in @InputArgument`() {
        val schema = """
            type Query {
                hello(type:GreetingType): String
            }
            
            enum GreetingType {          
                FRIENDLY
                POLITE
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument type: KGreetingType): String {
                assertThat(type).isInstanceOf(KGreetingType::class.java)

                return "Hello, this is a $type greeting"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(type: FRIENDLY)}""")
        assertThat(executionResult.errors.isEmpty()).isTrue
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, this is a FRIENDLY greeting", data["hello"])
        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Kotlin Optional enum in @InputArgument`() {
        val schema = """
            type Query {
                hello(type:GreetingType): String
            }
            
            enum GreetingType {          
                FRIENDLY
                POLITE
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument type: Optional<KGreetingType>): String {
                assertThat(type).isNotEmpty.get().isInstanceOf(KGreetingType::class.java)

                return "Hello, this is a ${type.get()} greeting"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(type: FRIENDLY)}""")
        assertThat(executionResult.errors.isEmpty()).isTrue
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, this is a FRIENDLY greeting", data["hello"])
        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `List of enums as @InputArgument`() {
        val schema = """
            type Query {
                khello(input: [GreetingType]): String
                jhello(input: [GreetingType]): String
            }
            
            enum GreetingType {          
                FRIENDLY
                POLITE
            }
        """.trimIndent()

        val fetcher = object {

            @DgsQuery
            fun khello(@InputArgument input: List<KGreetingType>): String {
                assertThat(input).isNotEmpty.hasOnlyElementsOfType(KGreetingType::class.java)
                return "Hello, this is a $input greeting"
            }
            @DgsQuery
            fun jhello(@InputArgument input: List<JGreetingType>): String {
                assertThat(input).isNotEmpty.hasOnlyElementsOfType(JGreetingType::class.java)
                return "Hello, this is a $input greeting"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute(
            """{
            |   khello(input: [FRIENDLY POLITE])
            |   jhello(input: [FRIENDLY POLITE])
            |}
            """.trimMargin()
        )
        assertThat(executionResult.errors.isEmpty()).isTrue
        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Kotlin Optional enum @InputArgument with null value`() {
        val schema = """
            type Query {
                hello(type:GreetingType): String
            }
            
            enum GreetingType {          
                FRIENDLY
                POLITE
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument type: Optional<KGreetingType>): String {
                if (!type.isPresent) {
                    return "Hello, this is a default greeting"
                }

                return "Hello, this is a ${type.get()} greeting"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello}""")
        assertThat(executionResult.errors.isEmpty()).isTrue
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, this is a default greeting", data["hello"])
        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Nullable enum @InputArgument`() {
        val schema = """
            type Query {
                hello(type:GreetingType): String
            }

            enum GreetingType {
                FRIENDLY
                POLITE
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument type: KGreetingType?): String {
                return "Hello, this is a ${type ?: "SAD"} greeting"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello}""")
        assertThat(executionResult.errors.isEmpty()).isTrue
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, this is a SAD greeting", data["hello"])
        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Java enum @InputArgument`() {
        val schema = """
            type Query {
                hello(type:GreetingType): String
            }
            
            enum GreetingType {          
                FRIENDLY
                POLITE
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument type: JGreetingType): String {
                assertThat(type).isInstanceOf(JGreetingType::class.java)

                return "Hello, this is a $type greeting"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(type: FRIENDLY)}""")
        assertThat(executionResult.errors.isEmpty()).isTrue
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, this is a FRIENDLY greeting", data["hello"])
        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Java optional enum @InputArgument`() {
        val schema = """
            type Query {
                hello(type:GreetingType): String
            }
            
            enum GreetingType {          
                FRIENDLY
                POLITE
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument type: Optional<JGreetingType>): String {
                assertThat(type).isNotEmpty.get().isInstanceOf(JGreetingType::class.java)

                return "Hello, this is a ${type.get()} greeting"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(type: FRIENDLY)}""")
        assertThat(executionResult.errors.isEmpty()).isTrue
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, this is a FRIENDLY greeting", data["hello"])
        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Java optional enum @InputArgument with null value`() {
        val schema = """
            type Query {
                hello(type:GreetingType): String
            }
            
            enum GreetingType {          
                FRIENDLY
                POLITE
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument type: Optional<JGreetingType>): String {
                if (!type.isPresent) {
                    return "Hello, this is a default greeting"
                }

                return "Hello, this is a ${type.get()} greeting"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello}""")
        assertThat(executionResult.errors.isEmpty()).isTrue
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, this is a default greeting", data["hello"])
        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Nested Kotlin enum @InputArgument`() {
        val schema = """
            type Query {
                hello(input:InputMessage): String
            }

            input InputMessage {
                type: GreetingType
                typeList:  [GreetingType!]
            }
            
            enum GreetingType {
                FRIENDLY
                POLITE
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun hello(@InputArgument input: KInputMessage): String {
                assertThat(input.type).isInstanceOf(KGreetingType::class.java)
                assertThat(input.type).isEqualTo(KGreetingType.FRIENDLY)
                assertThat(input.typeList).hasOnlyElementsOfType(KGreetingType::class.java)
                assertThat(input.typeList).contains(KGreetingType.FRIENDLY, KGreetingType.POLITE)
                return "Hello, this is a ${input.type} greeting with ${input.typeList}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute(
            """
                { hello(input: { type: FRIENDLY, typeList: [POLITE, FRIENDLY] }) }
            """.trimIndent()
        )

        assertThat(executionResult.errors).isEmpty()
        assertThat(executionResult)
            .extracting { it.getData<Map<String, *>>() }
            .extracting { it["hello"] }
            .isEqualTo("Hello, this is a FRIENDLY greeting with [POLITE, FRIENDLY]")

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Nested null enum @InputArgument`() {
        val schema = """
            type Query {
                hello(someInput:InputMessage!): String
            }

            input InputMessage {
                type: GreetingType
            }

            enum GreetingType {
                FRIENDLY
                POLITE
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument someInput: JInputMessage): String {
                return "Hello, this is a ${someInput.type ?: "SAD"} greeting"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(someInput: {type: null})}""")
        assertThat(executionResult.errors.isEmpty()).isTrue
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, this is a SAD greeting", data["hello"])
        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Nested Java enum @InputArgument`() {
        val schema = """
            type Query {
                hello(input:InputMessage): String
            }
            
            input InputMessage {
                type: GreetingType
                typeList:  [GreetingType!]
            }
            
            enum GreetingType {          
                FRIENDLY
                POLITE
            }
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun hello(@InputArgument input: JInputMessage): String {
                assertThat(input.type).isInstanceOf(JGreetingType::class.java)
                assertThat(input.type).isEqualTo(JGreetingType.FRIENDLY)
                assertThat(input.typeList).hasOnlyElementsOfType(JGreetingType::class.java)
                assertThat(input.typeList).contains(JGreetingType.FRIENDLY, JGreetingType.POLITE)
                return "Hello, this is a ${input.type} greeting with ${input.typeList}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute(
            """
                { hello(input: { type: FRIENDLY, typeList: [POLITE, FRIENDLY] }) }
            """.trimIndent()
        )

        assertThat(executionResult.errors).isEmpty()
        assertThat(executionResult)
            .extracting { it.getData<Map<String, *>>() }
            .extracting { it["hello"] }
            .isEqualTo("Hello, this is a FRIENDLY greeting with [POLITE, FRIENDLY]")

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `An argument not annotated with @InputArgument should fall back to argument name resolution`() {
        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(name: String): String {
                return "Hello, $name"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val schema = provider.schema()

        val build = GraphQL.newGraphQL(schema).build()
        val executionResult = build.execute("""{hello(name: "tester")}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Hello, tester", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `The Object scalar should be converted using the extended scalar`() {

        val schema = """
            type Query {
                hello(objects: [BarInput]): String
            }
            
            input BarInput {
                name: String
                value: Object
            }    
                  
            scalar Object
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument objects: List<KBarInput>): String {
                return objects.joinToString { "${it.name}: ${it.value}" }
            }

            @DgsRuntimeWiring
            fun addScalar(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
                return builder.scalar(ExtendedScalars.Object)
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(objects: [{name: "Test 1", value: 1}, {name: "Test 2", value: "two"}])}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Test 1: 1, Test 2: two", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `The Object scalar as top level input argument should be passed into a Map of String to Any`() {

        val schema = """
            type Query {
                hello(json: Object): String
            }                          
                  
            scalar Object
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument json: Map<String, Any>): String {
                return json.map { "${it.key}: ${it.value}" }.joinToString()
            }

            @DgsRuntimeWiring
            fun addScalar(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
                return builder.scalar(ExtendedScalars.Object)
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(json: {keyA: "value A", keyB: "value B"})}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("keyA: value A, keyB: value B", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `A field of an input type of type Any should be assigned the actual value and skip converting`() {

        val schema = """
            type Query {
                hello(filter: Filter): String
            }               
                       
            input Filter {
                query: Object
            }
                  
            scalar Object
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument filter: KFilter): String {
                return filter.toString()
            }

            @DgsRuntimeWiring
            fun addScalar(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
                return builder.scalar(ExtendedScalars.Object)
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(filter: {query: {and: ["title", "genre"]}})}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("KFilter(query={and=[title, genre]})", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `A field of an input type of type Object should be assigned the actual value and skip converting`() {

        val schema = """
            type Query {
                hello(filter: Filter): String
            }               
                       
            input Filter {
                query: Object
            }
                  
            scalar Object
        """.trimIndent()

        val fetcher = object {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument filter: JFilter): String {
                @SuppressWarnings("unchecked")
                val map = filter.query as? Map<*, *>
                return map.orEmpty().entries.joinToString { (key, value) -> "$key: $value" }
            }

            @DgsRuntimeWiring
            fun addScalar(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
                return builder.scalar(ExtendedScalars.Object)
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("helloFetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(filter: {query: {and: ["title", "genre"]}})}""")
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("and: [title, genre]", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `Input object of a subclass with a generic typed field`() {
        val schema = """
            type Query {
                movies(sortBy: [MovieSortBy]): String
            }
                                  
            input MovieSortBy {
                field: MovieSortByField
                direction: SortDirection = ASC
            }    
            
            enum MovieSortByField {
                TITLE,
                RELEASEDATE
            }
                  
            enum SortDirection {
                ASC,
                DESC
            }
        """.trimIndent()

        val fetcher = object {
            @DgsQuery
            fun movies(@InputArgument sortBy: List<JMovieSortBy>): String {
                return "Sorted by: ${sortBy.joinToString { "${it.field}" }}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            "helloFetcher" to fetcher
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute(
            """
            {
                movies(sortBy: 
                    [
                        {field: RELEASEDATE, direction: DESC}, 
                        {field: TITLE, direction: ASC}
                    ]
                )
             }
            """.trimIndent()
        )
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        Assertions.assertEquals("Sorted by: RELEASEDATE, TITLE", data["movies"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun `List of lists as @InputArgument on Java Types`() {
        val schema = """
            type Query {
                lists(input: ListOfListsOfFilters!): String
                enums(input: ListOfListsOfEnums!): String
                strings(input: ListOfListsOfStrings!): String
            }
            
            input ListOfListsOfFilters{
                lists:  [[[Filter]]]!
            }
            
            input ListOfListsOfEnums {
                lists:  [[[AnEnum]]]!
            }
            
            input ListOfListsOfStrings {
                lists:  [[[String]]]!
            }
            
            input Filter {
                query: Object
            }
            
            enum AnEnum {
                A, B, C 
            }
            
            scalar Object
        """.trimIndent()

        val fetcher = object {
            @DgsQuery
            fun lists(@InputArgument input: JListOfListsOfLists.JListOfListOfFilters): String {
                assertThat(input).isNotNull
                assertThat(input.lists)
                    .contains(
                        listOf(
                            listOf(JFilter(mapOf("foo" to "bar")), JFilter(mapOf("baz" to "buz"))),
                            listOf(JFilter(mapOf("bat" to "brat")))
                        )
                    )
                return "Ok"
            }

            @DgsQuery
            fun enums(@InputArgument input: JListOfListsOfLists.JListOfListOfEnums): String {
                assertThat(input).isNotNull
                assertThat(input.lists).contains(listOf(listOf(JEnum.A, JEnum.B), listOf(JEnum.C)))
                return "Ok"
            }

            @DgsQuery
            fun strings(@InputArgument input: JListOfListsOfLists.JListOfListOfStrings): String {
                assertThat(input).isNotNull
                assertThat(input.lists).contains(listOf(listOf("Foo", "Bar"), listOf("Baz")))
                return "Ok"
            }

            @DgsRuntimeWiring
            fun addScalar(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
                return builder.scalar(ExtendedScalars.Object)
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("fetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()

        val executionResult = build.execute(
            """
                {
                    lists(input:{ lists: [[ [ {query: {foo: "bar"}} {query: {baz: "buz"}}] [ {query: {bat: "brat"}}] ]] })
                    enums(input:{ lists: [[ [A B] [C] ]] })
                    strings(input:{ lists: [[ ["Foo" "Bar"] ["Baz"] ]] })
               }
            """.trimIndent()
        )

        assertThat(executionResult.errors).isEmpty()
        val data = executionResult.getData<Map<String, *>>()
        assertThat(data).hasEntrySatisfying("lists") { assertThat(it).isEqualTo("Ok") }
        assertThat(data).hasEntrySatisfying("enums") { assertThat(it).isEqualTo("Ok") }
        assertThat(data).hasEntrySatisfying("strings") { assertThat(it).isEqualTo("Ok") }
    }

    @Test
    fun `List of lists as @InputArgument on Kotlin Types`() {
        val schema = """
            type Query {
                lists(input: ListOfListsOfFilters!): String
                enums(input: ListOfListsOfEnums!): String
                strings(input: ListOfListsOfStrings!): String
            }
            
            input ListOfListsOfFilters{
                lists:  [[[Filter]]]!
            }
            
            input ListOfListsOfEnums {
                lists:  [[[AnEnum]]]!
            }
            
            input ListOfListsOfStrings {
                lists:  [[[String]]]!
            }
            
            input Filter {
                query: Object
            }
            
            enum AnEnum {
                A, B, C 
            }
            
            scalar Object
        """.trimIndent()

        val fetcher = object {

            @DgsQuery
            fun lists(@InputArgument input: KListOfListsOfLists.KListOfListOfFilters): String {
                assertThat(input).isNotNull
                assertThat(input.lists)
                    .contains(
                        listOf(
                            listOf(KFilter(mapOf("foo" to "bar")), KFilter(mapOf("baz" to "buz"))),
                            listOf(KFilter(mapOf("bat" to "brat")))
                        )
                    )
                return "Ok"
            }

            @DgsQuery
            fun enums(@InputArgument input: KListOfListsOfLists.KListOfListOfEnums): String {
                assertThat(input).isNotNull
                assertThat(input.lists).contains(listOf(listOf(KEnum.A, KEnum.B), listOf(KEnum.C)))
                return "Ok"
            }

            @DgsQuery
            fun strings(@InputArgument input: KListOfListsOfLists.KListOfListOfStrings): String {
                assertThat(input).isNotNull
                assertThat(input.lists).contains(listOf(listOf("Foo", "Bar"), listOf("Baz")))
                return "Ok"
            }

            @DgsRuntimeWiring
            fun addScalar(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
                return builder.scalar(ExtendedScalars.Object)
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("fetcher" to fetcher)
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()

        val executionResult = build.execute(
            """
                {
                    lists(input:{ lists: [[ [ {query: {foo: "bar"}} {query: {baz: "buz"}}] [ {query: {bat: "brat"}}] ]] })
                    enums(input:{ lists: [[ [A B] [C] ]] })
                    strings(input:{ lists: [[ ["Foo" "Bar"] ["Baz"] ]] })
               }
            """.trimIndent()
        )

        assertThat(executionResult.errors).isEmpty()
        val data = executionResult.getData<Map<String, *>>()
        assertThat(data).hasEntrySatisfying("lists") { assertThat(it).isEqualTo("Ok") }
        assertThat(data).hasEntrySatisfying("enums") { assertThat(it).isEqualTo("Ok") }
        assertThat(data).hasEntrySatisfying("strings") { assertThat(it).isEqualTo("Ok") }

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }
}
