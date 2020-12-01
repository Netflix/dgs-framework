package com.netflix.graphql.dgs

import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException
import com.netflix.graphql.dgs.exceptions.NoSchemaFoundException
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import graphql.ExceptionWhileDataFetching
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
import java.util.*

@Suppress("UNUSED_PARAMETER")
@ExtendWith(MockKExtension::class)
internal class DgsSchemaProviderTest {

    @MockK
    lateinit var applicationContextMock: ApplicationContext

    val defaultHelloFetcher = object : Any() {
        @DgsData(parentType="Query", field = "hello")
        fun someFetcher(): String {
            return "Hello"
        }
    }

    val defaultVideoFetcher = object : Any() {
        @DgsData(parentType="Query", field = "video")
        fun someFetcher(): Video {
            return Show("ShowA")
        }
    }

    @Test
    fun findSchemaFiles() {
        val findSchemaFiles = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty()).findSchemaFiles()
        assertThat(findSchemaFiles.size).isGreaterThan(1)
        assertEquals("schema1.graphqls", findSchemaFiles[0].filename)
    }

    @Test
    fun findSchemaFilesEmptyDir() {
        assertThrows<NoSchemaFoundException> { DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty()).findSchemaFiles("notexists") }
    }

    @Test
    fun allowNoSchemasWhenTypeRegistryProvided() {
        val findSchemaFiles = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.of(TypeDefinitionRegistry()), Optional.empty()).findSchemaFiles("noexists")
        assertEquals(0, findSchemaFiles.size)
    }

    @Test
    fun addFetchers() {
        val fetcher = object: Any() {
           @DgsData(parentType="Query", field="hello")
           fun someFetcher(dfe: DataFetchingEnvironment):String {
                return "Hello"
           }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", fetcher))
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = provider.schema()
        val build = GraphQL.newGraphQL(schema).build()
        assertHello(build)

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun addFetchersWithConvertedArguments() {
        val fetcher = object: Any() {
           @DgsData(parentType="Query", field="hello")
           fun someFetcher(@InputArgument("name") name: String):String {
                return "Hello, $name"
           }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", fetcher))
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


        val fetcher = object: Any() {
           @DgsData(parentType="Query", field="hello")
           fun someFetcher(@InputArgument("person") person: Person):String {
                return "Hello, ${person.name}"
           }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", fetcher))
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


        val fetcher = object: Any() {
           @DgsData(parentType="Query", field="hello")
           fun someFetcher(@InputArgument("names") names: List<String>):String {
                return "Hello, ${names.joinToString(", ") }"
           }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", fetcher))
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


        val fetcher = object: Any() {
            @DgsData(parentType="Query", field="hello")
            fun someFetcher(@InputArgument("person", collectionType = Person::class) person: List<Person>):String {
                return "Hello, ${person.joinToString(", ") { it.name}}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", fetcher))
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


        val fetcher = object: Any() {
            @DgsData(parentType="Query", field="hello")
            fun someFetcher(@InputArgument("person", collectionType = Person::class) person: Set<Person>):String {
                return "Hello, ${person.joinToString(", ") { it.name}}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", fetcher))
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


        val fetcher = object: Any() {
            @DgsData(parentType="Query", field="hello")
            fun someFetcher(@InputArgument("person", collectionType = String::class) person: List<String>):String {
                return "Hello, ${person.joinToString(", ") { it}}"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", fetcher))
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


        val fetcher = object: Any() {
           @DgsData(parentType="Query", field="hello")
           fun someFetcher(@InputArgument("person") person: Person?):String {
               if(person == null) {
                   return "Hello, Stranger"
               }

               return "Hello, ${person.name}"
           }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", fetcher))
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


        val fetcher = object: Any() {
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

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", fetcher))
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


        val fetcher = object: Any() {
            @DgsData(parentType="Query", field="hello")
            fun someFetcher(dfe:DataFetchingEnvironment, @InputArgument("capitalize") capitalize: Boolean, @InputArgument("person") person: Person):String {
                val otherArg:String = dfe.getArgument("otherArg")

                val msg = if(capitalize) {
                    "hello, ${person.name}".capitalize()
                } else {
                    "hello, ${person.name}"
                }
                return msg + otherArg
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", fetcher))
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


        val fetcher = object: Any() {
            @DgsData(parentType="Query", field="hello")
            fun someFetcher(@InputArgument("capitalize") capitalize: Boolean, @InputArgument("person") person: Person, dfe:DataFetchingEnvironment):String {
                val otherArg:String = dfe.getArgument("otherArg")

                val msg = if(capitalize) {
                    "hello, ${person.name}".capitalize()
                } else {
                    "hello, ${person.name}"
                }
                return msg + otherArg
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", fetcher))
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
    fun addFetchersWithUnknownArgument() {
        val schema = """
            type Query {
                hello: String
            }
        """.trimIndent()


        val fetcher = object: Any() {
            @DgsData(parentType="Query", field="hello")
            fun someFetcher(someArg:String?):String {

                return "Hello, $someArg"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", fetcher))
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
    fun addDefaultTypeResolvers() {
        val schema = """
            type Query {
                video: Video
            }

            interface Video {
                title: String
            }
        """.trimIndent()

        val resolverDefault = object: Any() {
            @DgsTypeResolver(name="Video")
            @DgsDefaultTypeResolver
            fun resolveType(type: Any): String? {
                println("Using default resolver")
                return null
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("defaultResolver", resolverDefault),
                Pair("videoFetcher", defaultVideoFetcher))
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

        val resolverDefault = object: Any() {
            @DgsTypeResolver(name="Video")
            @DgsDefaultTypeResolver
            fun resolveType(type: Any): String? {
                println("Using default resolver")
                return null
            }
        }

        val resolverOverride = object: Any() {
            @DgsTypeResolver(name="Video")
            fun resolveType(type: Any): String {
                println("Using override resolver")
                return "Show"
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("defaultResolver", resolverDefault),
                Pair("overrideResolver", resolverOverride), Pair("videoFetcher", defaultVideoFetcher))
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val build = GraphQL.newGraphQL(provider.schema(schema)).build()
        assertVideo(build)
    }

    @Test
    fun addFetchersWithoutDataFetchingEnvironment() {
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", defaultHelloFetcher))
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = provider.schema()
        val build = GraphQL.newGraphQL(schema).build()
        assertHello(build)

        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }

    @Test
    fun allowMergingStaticAndDynamicSchema() {
        val codeRegistry = object: Any() {
            @DgsCodeRegistry
            fun registry(codeRegistryBuilder: GraphQLCodeRegistry.Builder, registry: TypeDefinitionRegistry?): GraphQLCodeRegistry.Builder? {
                val df = DataFetcher { "Runtime added field" }
                val coordinates = FieldCoordinates.coordinates("Query", "myField")
                return codeRegistryBuilder.dataFetcher(coordinates, df)
            }

        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", defaultHelloFetcher), Pair("codeRegistry", codeRegistry))
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val typeDefinitionFactory = TypeDefinitionRegistry()
        val objectTypeExtensionDefinition = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition()
                .name("Query")
                .fieldDefinition(FieldDefinition.newFieldDefinition()
                        .name("myField")
                        .type(TypeName("String")).build())
                .build()

        typeDefinitionFactory.add(objectTypeExtensionDefinition)
        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.of(typeDefinitionFactory), Optional.empty())
        val schema = provider.schema()
        val build = GraphQL.newGraphQL(schema).build()
        assertHello(build)

        val executionResult2 = build.execute("{myField}")
        assertTrue(executionResult2.isDataPresent)

        val data = executionResult2.getData<Map<String,*>>()
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

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java)} returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java)} returns emptyMap()

        DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty()).schema(schema)
    }



    @Test
    fun notRequiredEntitiesFetcherWithoutFederation() {
        val schema = """
            type Movie {
                movieId: Int!
            }
        """.trimIndent()

        val dgsSchemaProvider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf()
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        assertThat(dgsSchemaProvider.schema(schema)).isNotNull

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
        val video = data["video"] as Map<String, *>
        assertEquals("ShowA", video["title"])
    }
}

interface Video {
    val title: String
}

data class Show(override val title: String) : Video
data class Person(val name: String)
