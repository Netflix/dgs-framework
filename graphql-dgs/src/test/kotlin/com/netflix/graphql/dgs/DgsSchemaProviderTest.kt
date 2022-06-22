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

import com.netflix.graphql.dgs.exceptions.InvalidTypeResolverException
import com.netflix.graphql.dgs.exceptions.NoSchemaFoundException
import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.kotlin.test.Show
import com.netflix.graphql.dgs.internal.kotlin.test.Video
import com.netflix.graphql.dgs.internal.method.DataFetchingEnvironmentArgumentResolver
import com.netflix.graphql.dgs.internal.method.InputArgumentResolver
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeExtensionDefinition
import graphql.language.TypeName
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.idl.TypeDefinitionRegistry
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.reactivestreams.Publisher
import org.springframework.context.ApplicationContext
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.reflect.full.findAnnotation

@Suppress("unused")
@ExtendWith(MockKExtension::class)
internal class DgsSchemaProviderTest {

    @MockK
    lateinit var applicationContextMock: ApplicationContext

    private fun schemaProvider(
        typeDefinitionRegistry: TypeDefinitionRegistry? = null,
        schemaLocations: List<String> = listOf(DgsSchemaProvider.DEFAULT_SCHEMA_LOCATION),
        componentFilter: (Any) -> Boolean = { true }
    ): DgsSchemaProvider {
        return DgsSchemaProvider(
            applicationContext = applicationContextMock,
            federationResolver = Optional.empty(),
            schemaLocations = schemaLocations,
            existingTypeDefinitionRegistry = Optional.ofNullable(typeDefinitionRegistry),
            methodDataFetcherFactory = MethodDataFetcherFactory(
                listOf(
                    InputArgumentResolver(DefaultInputObjectMapper()),
                    DataFetchingEnvironmentArgumentResolver()
                )
            ),
            componentFilter = componentFilter
        )
    }

    private val defaultHelloFetcher = object : Any() {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(): String {
            return "Hello"
        }
    }

    private val defaultVideoFetcher = object : Any() {
        @DgsData(parentType = "Query", field = "video")
        fun someFetcher(): Video {
            return Show("ShowA")
        }
    }

    private interface DefaultHelloFetcherInterface {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(): String
    }

    private val interfaceHelloFetcher = object : DefaultHelloFetcherInterface {
        override fun someFetcher(): String =
            "Hello"
    }

    @BeforeEach
    fun setupApplicationMockedContext() {
        withNoComponents()
        withNoScalars()
        withNoDirectives()
    }

    @Test
    fun findSchemaFiles() {
        val schemaFiles = schemaProvider().findSchemaFiles()
        assertThat(schemaFiles.size).isGreaterThan(1)
        assertEquals("schema1.graphqls", schemaFiles.first().filename)
    }

    @Test
    fun findMultipleSchemaFilesSingleLocation() {
        val schemaFiles = schemaProvider(schemaLocations = listOf("classpath*:location1/**/*.graphql*"))
            .findSchemaFiles()
        assertThat(schemaFiles.size).isGreaterThan(2)
        assertEquals("location1-schema1.graphqls", schemaFiles[0].filename)
        assertEquals("location1-schema2.graphqls", schemaFiles[1].filename)
    }

    @Test
    fun findMultipleSchemaFilesMultipleLocations() {
        val schemaFiles = schemaProvider(
            schemaLocations = listOf("classpath*:location1/**/*.graphql*", "classpath*:location2/**/*.graphql*")
        ).findSchemaFiles()
        assertThat(schemaFiles.size).isGreaterThan(4)
        assertEquals("location1-schema1.graphqls", schemaFiles[0].filename)
        assertEquals("location1-schema2.graphqls", schemaFiles[1].filename)
        assertEquals("location2-schema1.graphqls", schemaFiles[2].filename)
        assertEquals("location2-schema2.graphqls", schemaFiles[3].filename)
    }

    @Test
    fun findSchemaFilesEmptyDir() {
        assertThrows<NoSchemaFoundException> {
            schemaProvider(
                schemaLocations = listOf("classpath*:notexists/**/*.graphql*")
            ).findSchemaFiles()
        }
    }

    @Test
    fun allowNoSchemasWhenTypeRegistryProvided() {
        schemaProvider(
            typeDefinitionRegistry = TypeDefinitionRegistry(),
            schemaLocations = listOf("classpath*:notexists/**/*.graphql*")
        ).findSchemaFiles()
    }

    @Test
    fun addFetchers() {
        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(): String {
                return "Hello"
            }
        }

        withComponents("helloFetcher" to fetcher)

        val schemaProvider = schemaProvider()
        assertThat(schemaProvider.resolvedDataFetchers()).isEmpty()
        val schema = schemaProvider.schema()
        assertThat(schemaProvider.resolvedDataFetchers())
            .isNotEmpty.hasSize(1).first().satisfies(
                Consumer {
                    assertThat(it.parentType).isEqualTo("Query")
                    assertThat(it.field).isEqualTo("hello")
                }
            )

        val build = GraphQL.newGraphQL(schema).build()
        assertHello(build)

        verifyComponents()
    }

    @Test
    fun addPrivateFetchers() {
        val fetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            private fun someFetcher(): String {
                return "Hello"
            }
        }

        withComponents("helloFetcher" to fetcher)

        val schemaProvider = schemaProvider()
        assertThat(schemaProvider.resolvedDataFetchers()).isEmpty()
        val schema = schemaProvider.schema()
        assertThat(schemaProvider.resolvedDataFetchers())
            .isNotEmpty.hasSize(1).first().satisfies(
                Consumer {
                    assertThat(it.parentType).isEqualTo("Query")
                    assertThat(it.field).isEqualTo("hello")
                }
            )

        val build = GraphQL.newGraphQL(schema).build()
        assertHello(build)

        verifyComponents()
    }

    open class BaseClassFetcher {
        @DgsData(parentType = "Query", field = "hello")
        private fun someFetcher(): String {
            return "Hello"
        }
    }

    @Test
    fun addSubClassFetchers() {
        val fetcher = object : BaseClassFetcher() {
            // We're only interested in the base class for this test
        }

        withComponents("helloFetcher" to fetcher)

        val schema = schemaProvider().schema()
        val build = GraphQL.newGraphQL(schema).build()
        assertHello(build)

        verifyComponents()
    }

    @Test
    fun withNoTypeResolvers() {
        val schema = """
            type Query {
                video: Video
            }

            interface Video {
                title: String
            }
        """.trimIndent()

        withComponents("videoFetcher" to defaultVideoFetcher)
        val error: InvalidTypeResolverException = assertThrows {
            val build = GraphQL.newGraphQL(schemaProvider().schema(schema)).build()
            build.execute("{video{title}}")
        }
        assertThat(error.message).isEqualTo("The default type resolver could not find a suitable Java type for GraphQL interface type `Video`. Provide a @DgsTypeResolver for Show.")
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
            fun resolveType(@Suppress("unused_parameter") type: Any): String? {
                return null
            }
        }
        withComponents("defaultResolver" to resolverDefault, "videoFetcher" to defaultVideoFetcher)
        assertThatNoException().isThrownBy {
            // verify that it should not trigger a build failure
            GraphQL.newGraphQL(schemaProvider().schema(schema)).build()
        }
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
            fun resolveType(@Suppress("unused_parameter") type: Any): String? {
                fail { "We are not expecting to resolve via the default resolver" }
            }
        }

        val resolverOverride = object : Any() {
            @DgsTypeResolver(name = "Video")
            fun resolveType(@Suppress("unused_parameter") type: Any): String {
                return "Show"
            }
        }

        withComponents(
            "defaultResolver" to resolverDefault,
            "overrideResolver" to resolverOverride,
            "videoFetcher" to defaultVideoFetcher
        )

        val build = GraphQL.newGraphQL(schemaProvider().schema(schema)).build()
        assertVideo(build)
    }

    @Test
    fun addFetchersWithoutDataFetchingEnvironment() {
        withComponents("helloFetcher" to defaultHelloFetcher)

        val schema = schemaProvider().schema()
        val build = GraphQL.newGraphQL(schema).build()
        assertHello(build)

        verifyComponents()
    }

    @Test
    fun allowMergingStaticAndDynamicSchema() {
        val codeRegistry = object {
            @DgsCodeRegistry
            fun registry(
                codeRegistryBuilder: GraphQLCodeRegistry.Builder,
                @Suppress("unused_parameter") registry: TypeDefinitionRegistry?
            ): GraphQLCodeRegistry.Builder {
                val df = DataFetcher { "Runtime added field" }
                val coordinates = FieldCoordinates.coordinates("Query", "myField")
                return codeRegistryBuilder.dataFetcher(coordinates, df)
            }
        }

        withComponents("helloFetcher" to defaultHelloFetcher, "codeRegistry" to codeRegistry)

        val typeDefinitionRegistry = TypeDefinitionRegistry()
        val objectTypeExtensionDefinition = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition()
            .name("Query")
            .fieldDefinition(
                FieldDefinition.newFieldDefinition()
                    .name("myField")
                    .type(TypeName("String")).build()
            )
            .build()

        typeDefinitionRegistry.add(objectTypeExtensionDefinition)
        val schema = schemaProvider(typeDefinitionRegistry = typeDefinitionRegistry).schema()
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

        schemaProvider().schema(schema)
    }

    @Test
    fun notRequiredEntitiesFetcherWithoutFederation() {
        val schema = """
            type Movie {
                movieId: Int!
            }
        """.trimIndent()

        val dgsSchemaProvider = schemaProvider()
        assertThat(dgsSchemaProvider.schema(schema)).isNotNull
    }

    @Test
    fun enableInstrumentationForDataFetchers() {
        withComponents("helloFetcher" to defaultHelloFetcher)
        val provider = schemaProvider()
        provider.schema()
        assertThat(provider.isFieldInstrumentationEnabled("Query.hello")).isTrue
    }

    @Test
    fun enableInstrumentationForDataFetchersFromInterfaces() {
        withComponents("helloFetcher" to interfaceHelloFetcher)
        val schemaProvider = schemaProvider()
        schemaProvider.schema()
        assertThat(schemaProvider.isFieldInstrumentationEnabled("Query.hello")).isTrue
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
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val schemaProvider = schemaProvider()
        schemaProvider.schema()
        assertThat(schemaProvider.isFieldInstrumentationEnabled("Query.hello")).isFalse
    }

    @Test
    fun disableInstrumentationForAsyncDataFetchers() {
        val noTracingDataFetcher = object : Any() {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(): CompletableFuture<String> {
                return CompletableFuture.supplyAsync { "hello" }
            }
        }

        withComponents("helloFetcher" to noTracingDataFetcher)

        val schemaProvider = schemaProvider()
        schemaProvider.schema()
        assertThat(schemaProvider.isFieldInstrumentationEnabled("Query.hello")).isFalse
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

        withComponents("helloFetcher" to noTracingDataFetcher)
        val schemaProvider = schemaProvider()
        schemaProvider.schema()
        assertThat(schemaProvider.isFieldInstrumentationEnabled("Query.hello")).isTrue
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

        withComponents("videoFetcher" to defaultVideoFetcher, "titleFetcher" to titleFetcher)

        val schemaProvider = schemaProvider()
        schemaProvider.schema(schema)
        assertThat(schemaProvider.isFieldInstrumentationEnabled("Video.title")).isTrue
        assertThat(schemaProvider.isFieldInstrumentationEnabled("Show.title")).isTrue
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

        withComponents("videoFetcher" to defaultVideoFetcher, "titleFetcher" to titleFetcher)

        val schemaProvider = schemaProvider()
        schemaProvider.schema(schema)
        assertThat(schemaProvider.isFieldInstrumentationEnabled("Video.title")).isFalse
        assertThat(schemaProvider.isFieldInstrumentationEnabled("Show.title")).isFalse
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

        withComponents("videoFetcher" to defaultVideoFetcher, "titleFetcher" to titleFetcher)

        val schemaProvider = schemaProvider()
        schemaProvider.schema(schema)
        assertThat(schemaProvider.isFieldInstrumentationEnabled("Video.title")).isFalse
        assertThat(schemaProvider.isFieldInstrumentationEnabled("Show.title")).isFalse
    }

    @Test
    fun `DataFetcher with @DgsQuery annotation without field name`() {
        val fetcher = object : Any() {
            @DgsQuery
            fun hello(): String {
                return "Hello"
            }
        }

        withComponents("helloFetcher" to fetcher)

        val schemaProvider = schemaProvider()
        val schema = schemaProvider.schema()
        val build = GraphQL.newGraphQL(schema).build()
        assertHello(build)

        verifyComponents()
    }

    @Test
    fun `DataFetcher with @DgsData annotation without field name`() {
        val fetcher = object : Any() {
            @DgsData(parentType = "Query")
            fun hello(): String {
                return "Hello"
            }
        }

        withComponents("helloFetcher" to fetcher)

        val schemaProvider = schemaProvider()
        val schema = schemaProvider.schema()
        val build = GraphQL.newGraphQL(schema).build()
        assertHello(build)

        verifyComponents()
    }

    @Test
    fun `DataFetcher with @DgsQuery annotation with field name`() {
        val fetcher = object : Any() {
            @DgsQuery(field = "hello")
            fun someName(): String {
                return "Hello"
            }
        }

        withComponents("helloFetcher" to fetcher)

        val schema = schemaProvider().schema()
        val build = GraphQL.newGraphQL(schema).build()
        assertHello(build)

        verifyComponents()
    }

    @Test
    fun `DataFetcher with @DgsMutation annotation without field name`() {
        val fetcher = object : Any() {
            @DgsMutation
            fun addMessage(@InputArgument message: String): String {
                return message
            }
        }

        withComponents("helloFetcher" to fetcher)

        val schema = schemaProvider().schema(
            """
            type Mutation {
                addMessage(message: String): String
            }
            """.trimIndent()
        )
        val build = GraphQL.newGraphQL(schema).build()
        assertInputMessage(build)

        verifyComponents()
    }

    @Test
    fun `DataFetcher with @DgsMutation annotation with field name`() {
        val fetcher = object : Any() {
            @DgsMutation(field = "addMessage")
            fun someName(@InputArgument message: String): String {
                return message
            }
        }

        withComponents("helloFetcher" to fetcher)

        val schema = schemaProvider().schema(
            """
            type Mutation {
                addMessage(message: String): String
            }
            """.trimIndent()
        )
        val build = GraphQL.newGraphQL(schema).build()
        assertInputMessage(build)

        verifyComponents()
    }

    @Test
    fun `Subscription dataFetcher with @DgsSubscription annotation without field name`() {
        val fetcher = object : Any() {
            @DgsSubscription
            fun messages(): Publisher<String> {
                return Flux.just("hello")
            }
        }

        withComponents("helloFetcher" to fetcher)

        val schema = schemaProvider().schema(
            """
            type Subscription {
                messages: String
            }
            """.trimIndent()
        )
        val build = GraphQL.newGraphQL(schema).build()
        assertSubscription(build)

        verifyComponents()
    }

    @Test
    fun `Subscription dataFetcher with @DgsSubscription annotation with field name`() {
        val fetcher = object : Any() {
            @DgsSubscription(field = "messages")
            fun someMethod(): Publisher<String> {
                return Flux.just("hello")
            }
        }

        withComponents("helloFetcher" to fetcher)

        val schema = schemaProvider().schema(
            """
            type Subscription {
                messages: String
            }
            """.trimIndent()
        )
        val build = GraphQL.newGraphQL(schema).build()
        assertSubscription(build)

        verifyComponents()
    }

    annotation class TestAnnotation

    @Test
    fun `SchemaProvider with component filter`() {
        val fetcher1 = @TestAnnotation object : Any() {
            @DgsQuery(field = "hello")
            fun someName(): String {
                return "Goodbye"
            }
        }
        val fetcher2 = object : Any() {
            @DgsQuery(field = "hello")
            fun someName(): String {
                return "Hello"
            }
        }

        withComponents(
            "helloFetcher1" to fetcher1,
            "helloFetcher2" to fetcher2
        )

        val schema = schemaProvider(componentFilter = {
            it::class.findAnnotation<TestAnnotation>() == null
        }).schema()
        val build = GraphQL.newGraphQL(schema).build()
        assertHello(build)

        verifyComponents()
    }

    private fun assertHello(build: GraphQL) {
        val executionResult = build.execute("{hello}")
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        assertEquals("Hello", data["hello"])
    }

    private fun assertVideo(build: GraphQL) {
        val executionResult = build.execute("{video{title}}")
        assertThat(executionResult.isDataPresent).isTrue
        assertThat(executionResult.errors).isEmpty()
        val data = executionResult.getData<Map<String, *>>()
        assertThat(data).containsKey("video")
        assertThat(data["video"] as Map<*, *>).hasFieldOrPropertyWithValue("title", "ShowA")
    }

    private fun assertSubscription(build: GraphQL) {
        val executionResult = build.execute("subscription {messages}")
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Publisher<ExecutionResult>>()

        StepVerifier
            .create(data)
            .expectSubscription().assertNext { result ->
                assertThat(result.getData<Map<String, String>>())
                    .hasEntrySatisfying("messages") { value -> assertThat(value).isEqualTo("hello") }
            }
            .verifyComplete()
    }

    private fun assertInputMessage(build: GraphQL) {
        val executionResult = build.execute("""mutation {addMessage(message: "hello")}""")
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, *>>()
        assertEquals("hello", data["addMessage"])
    }

    private fun withComponents(vararg components: Pair<String, Any>) {
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(*components)
    }

    private fun withNoComponents() = withComponents()
    private fun withScalars(vararg scalars: Pair<String, Any>) {
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns mapOf(*scalars)
    }

    private fun withNoScalars() = withScalars()
    private fun withDirectives(vararg directives: Pair<String, Any>) {
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns mapOf(*directives)
    }

    private fun withNoDirectives() = withDirectives()

    private fun verifyComponents() {
        verify { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) }
    }
}
