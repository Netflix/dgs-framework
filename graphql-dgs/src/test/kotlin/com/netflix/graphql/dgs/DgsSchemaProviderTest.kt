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

package com.netflix.graphql.dgs

import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException
import com.netflix.graphql.dgs.exceptions.NoSchemaFoundException
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.scalars.UploadScalar
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeExtensionDefinition
import graphql.language.TypeName
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.idl.TypeDefinitionRegistry
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CompletableFuture


@Suppress("UNUSED_PARAMETER")
@ExtendWith(MockKExtension::class)
internal class DgsSchemaProviderTest {

    @MockK
    lateinit var applicationContextMock: ApplicationContext

    val defaultHelloFetcher = object : Any() {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(): String {
            return "Hello"
        }
    }

    val defaultVideoFetcher = object : Any() {
        @DgsData(parentType = "Query", field = "video")
        fun someFetcher(): Video {
            return Show("ShowA")
        }
    }

    @Test
    fun findSchemaFiles() {
        val findSchemaFiles = DgsSchemaProvider(
            applicationContextMock,
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        ).findSchemaFiles()
        assertThat(findSchemaFiles.size).isGreaterThan(1)
        assertEquals("schema1.graphqls", findSchemaFiles[0].filename)
    }

    @Test
    fun findSchemaFilesEmptyDir() {
        assertThrows<NoSchemaFoundException> {
            DgsSchemaProvider(
                applicationContextMock,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
            ).findSchemaFiles("notexists")
        }
    }

    @Test
    fun allowNoSchemasWhenTypeRegistryProvided() {
        val findSchemaFiles = DgsSchemaProvider(
            applicationContextMock,
            Optional.empty(),
            Optional.of(TypeDefinitionRegistry()),
            Optional.empty()
        ).findSchemaFiles("noexists")
        assertEquals(0, findSchemaFiles.size)
    }

    @Test
    fun addFetchers() {
        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(dfe: DataFetchingEnvironment): String {
                return "Hello"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = provider.schema()
        val build = GraphQL.newGraphQL(schema).build()
        assertHello(build)

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun addFetchersWithConvertedArguments() {
        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("name") name: String): String {
                return "Hello, $name"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = provider.schema()

        val build = GraphQL.newGraphQL(schema).build()
        val executionResult = build.execute("""{hello(name: "tester")}""")
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        assertEquals("Hello, tester", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun addFetchersWithConvertedInputTypeArguments() {
        val schema = """
            type Query {
                hello(person:Person): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()


        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("person") person: Person): String {
                return "Hello, ${person.name}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(person: {name: "tester"})}""")
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        assertEquals("Hello, tester", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun addFetchersWithConvertedInputTypeListArguments() {
        val schema = """
            type Query {
                hello(names: [String]): String
            }
        """.trimIndent()


        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("names") names: List<String>): String {
                return "Hello, ${names.joinToString(", ")}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(names: ["tester 1", "tester 2"])}""")
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        assertEquals("Hello, tester 1, tester 2", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun addFetchersWithConvertedInputTypeListObjectArguments() {
        val schema = """
            type Query {
                hello(person:[Person]): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()


        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("person", collectionType = Person::class) person: List<Person>): String {
                return "Hello, ${person.joinToString(", ") { it.name }}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(person: [{name: "tester"}, {name: "tester 2"}])}""")
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        assertEquals("Hello, tester, tester 2", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun addFetchersWithConvertedInputTypeInvalidCollectionType() {
        val schema = """
            type Query {
                hello(person:[Person]): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()


        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("person", collectionType = Person::class) person: Set<Person>): String {
                return "Hello, ${person.joinToString(", ") { it.name }}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()


        val executionResult = build.execute("""{hello(person: [{name: "tester"}, {name: "tester 2"}])}""")
        assertThat(executionResult.errors.size).isEqualTo(1)
        val exceptionWhileDataFetching = executionResult.errors[0] as ExceptionWhileDataFetching
        assertThat(exceptionWhileDataFetching.exception).isInstanceOf(DgsInvalidInputArgumentException::class.java)
        assertThat(exceptionWhileDataFetching.exception.message).contains("Specified type 'interface java.util.Set' is invalid. Found java.util.ArrayList instead")


        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun addFetchersWithConvertedInputTypeInvalidItemType() {
        val schema = """
            type Query {
                hello(person:[Person]): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()


        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("person", collectionType = String::class) person: List<String>): String {
                return "Hello, ${person.joinToString(", ") { it }}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()


        val executionResult = build.execute("""{hello(person: [{name: "tester"}, {name: "tester 2"}])}""")
        assertThat(executionResult.errors.size).isEqualTo(1)
        val exceptionWhileDataFetching = executionResult.errors[0] as ExceptionWhileDataFetching
        assertThat(exceptionWhileDataFetching.exception).isInstanceOf(DgsInvalidInputArgumentException::class.java)
        assertThat(exceptionWhileDataFetching.exception.message).contains("Specified type 'class java.lang.String' is invalid for person")


        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun addFetchersWithNullInputArgument() {
        val schema = """
            type Query {
                hello(person:Person): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()


        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(@InputArgument("person") person: Person?): String {
                if (person == null) {
                    return "Hello, Stranger"
                }

                return "Hello, ${person.name}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello}""")
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        assertEquals("Hello, Stranger", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun addFetchersWithMultipleConvertedInputTypeArguments() {
        val schema = """
            type Query {
                hello(person:Person, capitalize:Boolean): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()


        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(
                @InputArgument("capitalize") capitalize: Boolean,
                @InputArgument("person") person: Person
            ): String {
                return if (capitalize) {
                    "hello, ${person.name}".capitalize()
                } else {
                    "hello, ${person.name}"
                }
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(capitalize: true, person: {name: "tester"})}""")
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        assertEquals("Hello, tester", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun addFetchersWithMultipleConvertedInputTypeArgumentsAndDataFetchingEnv() {
        val schema = """
            type Query {
                hello(person:Person, capitalize:Boolean, otherArg:String): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()


        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(
                dfe: DataFetchingEnvironment,
                @InputArgument("capitalize") capitalize: Boolean,
                @InputArgument("person") person: Person
            ): String {
                val otherArg: String = dfe.getArgument("otherArg")

                val msg = if (capitalize) {
                    "hello, ${person.name}".capitalize()
                } else {
                    "hello, ${person.name}"
                }
                return msg + otherArg
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(capitalize: true, person: {name: "tester"}, otherArg: "!")}""")
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        assertEquals("Hello, tester!", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun addFetchersWithMultipleConvertedInputTypeArgumentsAndDataFetchingEnvDifferentOrder() {
        val schema = """
            type Query {
                hello(person:Person, capitalize:Boolean, otherArg:String): String
            }
            
            input Person {
                name:String
            }
        """.trimIndent()


        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(
                @InputArgument("capitalize") capitalize: Boolean,
                @InputArgument("person") person: Person,
                dfe: DataFetchingEnvironment
            ): String {
                val otherArg: String = dfe.getArgument("otherArg")

                val msg = if (capitalize) {
                    "hello, ${person.name}".capitalize()
                } else {
                    "hello, ${person.name}"
                }
                return msg + otherArg
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello(capitalize: true, person: {name: "tester"}, otherArg: "!")}""")
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        assertEquals("Hello, tester!", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun addFetchersWithMultipartFileInputArgument() {
        val schema = """
            type Mutation {
                upload(file: Upload): String
            }

            scalar Upload
        """.trimIndent()


        val fetcher = object : Any() {
            @DgsData(parentType = "Mutation", field = "upload")
            fun someFetcher(@InputArgument("file") file: MultipartFile): String {
                return String(file.bytes)
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            ),
            Pair("Upload", UploadScalar()),
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())


        val file: MultipartFile =
            MockMultipartFile("hello.txt", "hello.txt", MediaType.TEXT_PLAIN_VALUE, "Hello World".toByteArray())

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute(
            ExecutionInput.newExecutionInput().query("mutation(\$input: Upload!)  { upload(file: \$input) }")
                .variables(mapOf(Pair("input", file)))
        )
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        assertEquals("Hello World", data["upload"])
    }

    @Test
    fun addFetchersWithUnknownArgument() {
        val schema = """
            type Query {
                hello: String
            }
        """.trimIndent()


        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(someArg: String?): String {

                return "Hello, $someArg"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""{hello}""")
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        assertEquals("Hello, null", data["hello"])

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun addFetchersWithScalarInputArgument() {
        val schema = """
            type Mutation {
                setDate(date:DateTime): String
            }                     
            
            scalar DateTime
        """.trimIndent()


        val fetcher = object : Any() {
            @DgsData(parentType = "Mutation", field = "setDate")
            fun someFetcher(@InputArgument("date") date: LocalDateTime): String {
                return "The date is: ${date.format(DateTimeFormatter.ISO_DATE)}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                fetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns mapOf(Pair("DateTime", LocalDateTimeScalar()))

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())

        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        val executionResult = build.execute("""mutation {setDate(date: "2021-01-27T10:15:30")}""")
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        assertEquals("The date is: 2021-01-27", data["setDate"])
    }

    @Test
    fun addDefaultTypeResolvers() {
        val schema = """
            type Query {
                video: Video
            }

            interface Video {
                title: String
            }
        """.trimIndent()

        val resolverDefault = object : Any() {
            @DgsTypeResolver(name = "Video")
            @DgsDefaultTypeResolver
            fun resolveType(type: Any): String? {
                println("Using default resolver")
                return null
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair("defaultResolver", resolverDefault),
            Pair("videoFetcher", defaultVideoFetcher)
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        // verify that it should not trigger a build failure
        GraphQL.newGraphQL(provider.schema(schema)).build()
    }

    @Test
    fun addOverrideTypeResolvers() {
        val schema = """
            type Query {
                video: Video
            }

            interface Video {
                title: String
            }

            type Show implements Video {
                title: String
            }
        """.trimIndent()

        val resolverDefault = object : Any() {
            @DgsTypeResolver(name = "Video")
            @DgsDefaultTypeResolver
            fun resolveType(type: Any): String? {
                println("Using default resolver")
                return null
            }
        }

        val resolverOverride = object : Any() {
            @DgsTypeResolver(name = "Video")
            fun resolveType(type: Any): String {
                println("Using override resolver")
                return "Show"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair("defaultResolver", resolverDefault),
            Pair("overrideResolver", resolverOverride), Pair("videoFetcher", defaultVideoFetcher)
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        assertVideo(build)
    }

    @Test
    fun addFetchersWithoutDataFetchingEnvironment() {
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                defaultHelloFetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = provider.schema()
        val build = GraphQL.newGraphQL(schema).build()
        assertHello(build)

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun allowMergingStaticAndDynamicSchema() {
        val codeRegistry = object : Any() {
            @DgsCodeRegistry
            fun registry(
                codeRegistryBuilder: GraphQLCodeRegistry.Builder,
                registry: TypeDefinitionRegistry?
            ): GraphQLCodeRegistry.Builder? {
                val df = DataFetcher { "Runtime added field" }
                val coordinates = FieldCoordinates.coordinates("Query", "myField")
                return codeRegistryBuilder.dataFetcher(coordinates, df)
            }

        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                defaultHelloFetcher
            ), Pair("codeRegistry", codeRegistry)
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val typeDefinitionFactory = TypeDefinitionRegistry()
        val objectTypeExtensionDefinition = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition()
            .name("Query")
            .fieldDefinition(
                FieldDefinition.newFieldDefinition()
                    .name("myField")
                    .type(TypeName("String")).build()
            )
            .build()

        typeDefinitionFactory.add(objectTypeExtensionDefinition)
        val provider = DgsSchemaProvider(
            applicationContextMock,
            Optional.empty(),
            Optional.of(typeDefinitionFactory),
            Optional.empty()
        )
        val schema = provider.schema()
        val build = GraphQL.newGraphQL(schema).build()
        assertHello(build)

        val executionResult2 = build.execute("{myField}")
        assertTrue(executionResult2.isDataPresent)

        val data = executionResult2.getData<Map<String, *>>()
        assertEquals("Runtime added field", data["myField"])
    }

    @Test
    fun defaultEntitiesFetcher() {
        val schema = """
            type Movie @key(fields: "movieId") {
            movieId: Int!
            originalTitle: String
        }
        """.trimIndent()

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty()).schema(schema)
    }


    @Test
    fun notRequiredEntitiesFetcherWithoutFederation() {
        val schema = """
            type Movie {
                movieId: Int!
            }
        """.trimIndent()

        val dgsSchemaProvider =
            DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf()
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        assertThat(dgsSchemaProvider.schema(schema)).isNotNull

    }

    @Test
    fun enableInstrumentationForDataFetchers() {
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                defaultHelloFetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        provider.schema()
        assertThat(provider.dataFetcherInstrumentationEnabled).containsKey("Query.hello")
        assertThat(provider.dataFetcherInstrumentationEnabled["Query.hello"]).isTrue

    }

    @Test
    fun disableInstrumentationForDataFetchersWithAnnotation() {

        val noTracingDataFetcher = object : Any() {
            @DgsEnableDataFetcherInstrumentation(false)
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(): String {
                return "Hello"
            }
        }
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                noTracingDataFetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        provider.schema()
        assertThat(provider.dataFetcherInstrumentationEnabled).containsKey("Query.hello")
        assertThat(provider.dataFetcherInstrumentationEnabled["Query.hello"]).isFalse
    }

    @Test
    fun disableInstrumentationForAsyncDataFetchers() {
        val noTracingDataFetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(): CompletableFuture<String> {
                return CompletableFuture.supplyAsync { "hello" }
            }
        }
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                noTracingDataFetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        provider.schema()
        assertThat(provider.dataFetcherInstrumentationEnabled).containsKey("Query.hello")
        assertThat(provider.dataFetcherInstrumentationEnabled["Query.hello"]).isFalse
    }

    @Test
    fun enableInstrumentationForAsyncDataFetchersWithAnnotation() {
        val noTracingDataFetcher = object : Any() {
            @DgsEnableDataFetcherInstrumentation(true)
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(): CompletableFuture<String> {
                return CompletableFuture.supplyAsync { "hello" }
            }
        }
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "helloFetcher",
                noTracingDataFetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        provider.schema()
        assertThat(provider.dataFetcherInstrumentationEnabled).containsKey("Query.hello")
        assertThat(provider.dataFetcherInstrumentationEnabled["Query.hello"]).isTrue
    }

    @Test
    fun enableInstrumentationForInterfaceDataFetcher() {

        val schema = """
              type Query {
                video: Video
            }

            interface Video {
                title: String
            }

            type Show implements Video {
                title: String
            }                   
        """.trimIndent()

        val titleFetcher = object : Any() {
            @DgsData(parentType = "Video", field = "title")
            fun someFetcher(): String {
                return "Title on Interface"
            }
        }
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "videoFetcher",
                defaultVideoFetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "titleFetcher",
                titleFetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        provider.schema(schema)
        assertThat(provider.dataFetcherInstrumentationEnabled).containsKey("Video.title")
        assertThat(provider.dataFetcherInstrumentationEnabled["Video.title"]).isTrue

        assertThat(provider.dataFetcherInstrumentationEnabled).containsKey("Show.title")
        assertThat(provider.dataFetcherInstrumentationEnabled["Show.title"]).isTrue
    }

    @Test
    fun disableInstrumentationForInterfaceDataFetcherWithAnnotation() {

        val schema = """
              type Query {
                video: Video
            }

            interface Video {
                title: String
            }

            type Show implements Video {
                title: String
            }                   
        """.trimIndent()

        val titleFetcher = object : Any() {
            @DgsEnableDataFetcherInstrumentation(false)
            @DgsData(parentType = "Video", field = "title")
            fun someFetcher(): String {
                return "Title on Interface"
            }
        }
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "videoFetcher",
                defaultVideoFetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "titleFetcher",
                titleFetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        provider.schema(schema)
        assertThat(provider.dataFetcherInstrumentationEnabled).containsKey("Video.title")
        assertThat(provider.dataFetcherInstrumentationEnabled["Video.title"]).isFalse

        assertThat(provider.dataFetcherInstrumentationEnabled).containsKey("Show.title")
        assertThat(provider.dataFetcherInstrumentationEnabled["Show.title"]).isFalse
    }

    @Test
    fun disableInstrumentationForAsyncInterfaceDataFetcher() {

        val schema = """
              type Query {
                video: Video
            }

            interface Video {
                title: String
            }

            type Show implements Video {
                title: String
            }                   
        """.trimIndent()

        val titleFetcher = object : Any() {
            @DgsData(parentType = "Video", field = "title")
            fun someFetcher(): CompletableFuture<String> {
                return CompletableFuture.supplyAsync { "Title on Interface" }
            }
        }
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "videoFetcher",
                defaultVideoFetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
            Pair(
                "titleFetcher",
                titleFetcher
            )
        )
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        provider.schema(schema)
        assertThat(provider.dataFetcherInstrumentationEnabled).containsKey("Video.title")
        assertThat(provider.dataFetcherInstrumentationEnabled["Video.title"]).isFalse

        assertThat(provider.dataFetcherInstrumentationEnabled).containsKey("Show.title")
        assertThat(provider.dataFetcherInstrumentationEnabled["Show.title"]).isFalse
    }

    private fun assertHello(build: GraphQL) {
        val executionResult = build.execute("{hello}")
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        assertEquals("Hello", data["hello"])
    }

    private fun assertVideo(build: GraphQL) {
        val executionResult = build.execute("{video{title}}")
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        val video = data["video"] as Map<*, *>
        assertEquals("ShowA", video["title"])
    }
}

interface Video {
    val title: String
}

data class Show(override val title: String) : Video
data class Person(val name: String)
