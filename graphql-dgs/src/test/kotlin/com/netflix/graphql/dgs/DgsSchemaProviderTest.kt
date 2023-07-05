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

import com.netflix.graphql.dgs.exceptions.DataFetcherSchemaMismatchException
import com.netflix.graphql.dgs.exceptions.InvalidDgsConfigurationException
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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import org.reactivestreams.Publisher
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.reflect.KClass
import kotlin.reflect.full.hasAnnotation

internal class DgsSchemaProviderTest {

    private val contextRunner = ApplicationContextRunner()

    data class MovieSearch(val title: String, val length: Int)

    data class SeriesSearch(val name: String, val episodes: Int)

    private fun schemaProvider(
        applicationContext: ApplicationContext,
        typeDefinitionRegistry: TypeDefinitionRegistry? = null,
        schemaLocations: List<String> = listOf(DgsSchemaProvider.DEFAULT_SCHEMA_LOCATION),
        componentFilter: ((Any) -> Boolean)? = null,
        schemaWiringValidationEnabled: Boolean = true
    ): DgsSchemaProvider {
        return DgsSchemaProvider(
            applicationContext = applicationContext,
            federationResolver = Optional.empty(),
            schemaLocations = schemaLocations,
            existingTypeDefinitionRegistry = Optional.ofNullable(typeDefinitionRegistry),
            methodDataFetcherFactory = MethodDataFetcherFactory(
                listOf(
                    InputArgumentResolver(DefaultInputObjectMapper()),
                    DataFetchingEnvironmentArgumentResolver()
                )
            ),
            componentFilter = componentFilter,
            schemaWiringValidationEnabled = schemaWiringValidationEnabled
        )
    }

    @DgsComponent
    class HelloFetcher {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(): String {
            return "Hello"
        }
    }

    @DgsComponent
    class VideoFetcher {
        @DgsData(parentType = "Query", field = "video")
        fun someFetcher(): Video {
            return Show("ShowA")
        }
    }

    private interface DefaultHelloFetcherInterface {
        @DgsData(parentType = "Query", field = "hello")
        fun someFetcher(): String
    }

    @DgsComponent
    class FetcherImplementingInterface : DefaultHelloFetcherInterface {
        override fun someFetcher(): String =
            "Hello"
    }

    @DgsComponent
    class SearchFetcher {
        @DgsData(parentType = "Query", field = "search")
        fun someFetcher(): List<Any> {
            return listOf(
                MovieSearch("Extraction", 90),
                SeriesSearch("The Witcher", 15)
            )
        }
    }

    @Test
    fun findSchemaFiles() {
        contextRunner.run { context ->
            val schemaFiles = schemaProvider(applicationContext = context).findSchemaFiles()
            assertThat(schemaFiles.size).isGreaterThan(1)
            assertEquals("schema1.graphqls", schemaFiles.first().filename)
        }
    }

    @Test
    fun findMultipleSchemaFilesSingleLocation() {
        contextRunner.run { context ->
            val schemaFiles = schemaProvider(applicationContext = context, schemaLocations = listOf("classpath*:location1/**/*.graphql*"))
                .findSchemaFiles()
            assertThat(schemaFiles.size).isGreaterThan(2)
            assertEquals("location1-schema1.graphqls", schemaFiles[0].filename)
            assertEquals("location1-schema2.graphqls", schemaFiles[1].filename)
        }
    }

    @Test
    fun findMultipleSchemaFilesMultipleLocations() {
        contextRunner.run { context ->
            val schemaFiles = schemaProvider(
                applicationContext = context,
                schemaLocations = listOf("classpath*:location1/**/*.graphql*", "classpath*:location2/**/*.graphql*")
            ).findSchemaFiles()
            assertThat(schemaFiles.size).isGreaterThan(4)
            assertEquals("location1-schema1.graphqls", schemaFiles[0].filename)
            assertEquals("location1-schema2.graphqls", schemaFiles[1].filename)
            assertEquals("location2-schema1.graphqls", schemaFiles[2].filename)
            assertEquals("location2-schema2.graphqls", schemaFiles[3].filename)
        }
    }

    @Test
    fun `Should specify sourceName on SourceLocation`() {
        contextRunner.run { context ->
            val schemaProvider = schemaProvider(
                applicationContext = context,
                schemaLocations = listOf("classpath*:schema/**/*.graphql*")
            )

            val schema = schemaProvider.schema()

            for (type in schema.allTypesAsList) {
                if (type.definition?.sourceLocation != null) {
                    assertNotNull(type.definition?.sourceLocation?.sourceName)
                }
            }
        }
    }

    @Test
    fun findSchemaFilesEmptyDir() {
        contextRunner.run { context ->
            assertThrows<NoSchemaFoundException> {
                schemaProvider(
                    applicationContext = context,
                    schemaLocations = listOf("classpath*:notexists/**/*.graphql*")
                ).findSchemaFiles()
            }
        }
    }

    @Test
    fun allowNoSchemasWhenTypeRegistryProvided() {
        contextRunner.run { context ->
            schemaProvider(
                applicationContext = context,
                typeDefinitionRegistry = TypeDefinitionRegistry(),
                schemaLocations = listOf("classpath*:notexists/**/*.graphql*")
            ).findSchemaFiles()
        }
    }

    @Test
    fun findSchemaFilesIgnoreNonGraphQLFiles() {
        contextRunner.run { context ->
            val schemaFiles = schemaProvider(
                applicationContext = context,
                schemaLocations = listOf("classpath*:location3/**/*.graphql*")
            ).findSchemaFiles()
            assertEquals("location3-schema1.graphql", schemaFiles[0].filename)
            assertEquals("location3-schema2.graphqls", schemaFiles[1].filename)

            // Check that the .graphqlconfig file has been ignored
            val schemaFilesNames = mutableListOf<String>()
            for (schemaFile in schemaFiles) {
                schemaFilesNames.add(schemaFile.filename ?: error(""))
            }
            assertTrue(!schemaFilesNames.contains("location3-ignore.graphqlconfig"))
        }
    }

    @Test
    fun addFetchers() {
        @DgsComponent
        class Fetcher {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(): String {
                return "Hello"
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
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
        }
    }

    @Test
    fun addPrivateFetchers() {
        @DgsComponent
        class Fetcher {
            @DgsData(parentType = "Query", field = "hello")
            private fun someFetcher(): String {
                return "Hello"
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
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
        }
    }

    @Test
    fun withDuplicateFetchers() {
        @DgsComponent
        class Fetcher {
            @DgsData(parentType = "Query", field = "hello")
            fun fetcher1(): String {
                return "fetcher1"
            }

            @DgsData(parentType = "Query", field = "hello")
            fun fetcher2(): String {
                return "fetcher2"
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val exc = assertThrows<InvalidDgsConfigurationException> {
                GraphQL.newGraphQL(schemaProvider(applicationContext = context).schema()).build()
            }
            assertThat(exc.message).isEqualTo("Duplicate data fetchers registered for Query.hello")
        }
    }

    open class BaseClassFetcher {
        @DgsData(parentType = "Query", field = "hello")
        private fun someFetcher(): String {
            return "Hello"
        }
    }

    @Test
    fun addSubClassFetchers() {
        @DgsComponent
        class Fetcher : BaseClassFetcher() {
            // We're only interested in the base class for this test
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schema = schemaProvider(applicationContext = context).schema()
            val build = GraphQL.newGraphQL(schema).build()
            assertHello(build)
        }
    }

    @Test
    fun withNoTypeResolversOfInterface() {
        val schema = """
            type Query {
                video: Video
            }

            interface Video {
                title: String
            }
        """.trimIndent()

        contextRunner.withBeans(VideoFetcher::class).run { context ->
            val error = assertThrows<InvalidTypeResolverException> {
                val build = GraphQL.newGraphQL(schemaProvider(applicationContext = context).schema(schema)).build()
                build.execute("{video{title}}")
            }
            assertThat(error.message).isEqualTo("The default type resolver could not find a suitable Java type for GraphQL interface type `Video`. Provide a @DgsTypeResolver for `Show`.")
        }
    }

    @Test
    fun withNoTypeResolversOfUnion() {
        val schema = """
            type Query {
                search: [SearchResult]
            }
            
            union SearchResult = MovieSearchResult | SeriesSearchResult
            
            type MovieSearchResult {
                title: String
                length: Int
            }
            
            type SeriesSearchResult {
                title: String
                episodes: Int
            }
        """.trimIndent()

        contextRunner.withBean(SearchFetcher::class.java).run { context ->
            val error = assertThrows<InvalidTypeResolverException> {
                val build = GraphQL.newGraphQL(schemaProvider(applicationContext = context).schema(schema)).build()
                build.execute(
                    """
                     query {
                        search {
                            ...on MovieSearchResult {
                                title
                                length
                            }
                            ...on SeriesSearchResult {
                                title
                                episodes
                            }
                        }
                    }
                    """.trimIndent()
                )
            }
            assertThat(error.message).isEqualTo("The default type resolver could not find a suitable Java type for GraphQL union type `SearchResult`. Provide a @DgsTypeResolver for `MovieSearch`.")
        }
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

        @DgsComponent
        class FetcherWithDefaultResolver {
            @DgsTypeResolver(name = "Video")
            @DgsDefaultTypeResolver
            fun resolveType(@Suppress("unused_parameter") type: Any): String? {
                return null
            }
        }

        contextRunner.withBean(FetcherWithDefaultResolver::class.java).withBean(VideoFetcher::class.java).run { context ->
            assertThatNoException().isThrownBy {
                // verify that it should not trigger a build failure
                GraphQL.newGraphQL(schemaProvider(applicationContext = context).schema(schema)).build()
            }
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

        @DgsComponent
        class FetcherWithDefaultResolver {
            @DgsTypeResolver(name = "Video")
            @DgsDefaultTypeResolver
            fun resolveType(@Suppress("unused_parameter") type: Any): String? {
                fail { "We are not expecting to resolve via the default resolver" }
            }
        }

        @DgsComponent
        class FetcherWithResolverOverride {
            @DgsTypeResolver(name = "Video")
            fun resolveType(@Suppress("unused_parameter") type: Any): String {
                return "Show"
            }
        }

        contextRunner.withBeans(FetcherWithDefaultResolver::class, FetcherWithResolverOverride::class, VideoFetcher::class).run { context ->
            val build = GraphQL.newGraphQL(schemaProvider(applicationContext = context).schema(schema)).build()
            assertVideo(build)
        }
    }

    @Test
    fun addFetchersWithoutDataFetchingEnvironment() {
        contextRunner.withBeans(HelloFetcher::class).run { context ->
            val schema = schemaProvider(applicationContext = context).schema()
            val build = GraphQL.newGraphQL(schema).build()
            assertHello(build)
        }
    }

    @Test
    fun allowMergingStaticAndDynamicSchema() {
        @DgsComponent
        class CodeRegistryComponent {
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

        contextRunner.withBeans(HelloFetcher::class, CodeRegistryComponent::class).run { context ->
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
            val schema = schemaProvider(applicationContext = context, typeDefinitionRegistry = typeDefinitionRegistry).schema()
            val build = GraphQL.newGraphQL(schema).build()
            assertHello(build)

            val executionResult2 = build.execute("{myField}")
            assertTrue(executionResult2.isDataPresent)

            val data = executionResult2.getData<Map<String, *>>()
            assertEquals("Runtime added field", data["myField"])
        }

        contextRunner.withBeans(HelloFetcher::class, CodeRegistryComponent::class).run { context ->
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
            val schema = schemaProvider(applicationContext = context, typeDefinitionRegistry = typeDefinitionRegistry).schema()
            val build = GraphQL.newGraphQL(schema).build()
            assertHello(build)

            val executionResult2 = build.execute("{myField}")
            assertTrue(executionResult2.isDataPresent)

            val data = executionResult2.getData<Map<String, *>>()
            assertEquals("Runtime added field", data["myField"])
        }
    }

    @Test
    fun defaultEntitiesFetcher() {
        val schema = """
            type Movie @key(fields: "movieId") {
            movieId: Int!
            originalTitle: String
        }
        """.trimIndent()

        contextRunner.run { context ->
            schemaProvider(applicationContext = context).schema(schema)
        }
    }

    @Test
    fun notRequiredEntitiesFetcherWithoutFederation() {
        val schema = """
            type Movie {
                movieId: Int!
            }
        """.trimIndent()

        contextRunner.run { context ->
            val dgsSchemaProvider = schemaProvider(applicationContext = context)
            assertThat(dgsSchemaProvider.schema(schema)).isNotNull
        }
    }

    @Test
    fun enableInstrumentationForDataFetchers() {
        contextRunner.withBeans(HelloFetcher::class).run { context ->
            val provider = schemaProvider(applicationContext = context)
            provider.schema()
            assertThat(provider.isFieldInstrumentationEnabled("Query.hello")).isTrue
        }
    }

    @Test
    fun enableInstrumentationForDataFetchersFromInterfaces() {
        contextRunner.withBeans(FetcherImplementingInterface::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
            schemaProvider.schema()
            assertThat(schemaProvider.isFieldInstrumentationEnabled("Query.hello")).isTrue
        }
    }

    @Test
    fun disableInstrumentationForDataFetchersWithAnnotation() {
        @DgsComponent
        class NoTracingFetcher {
            @DgsEnableDataFetcherInstrumentation(false)
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(): String {
                return "Hello"
            }
        }

        contextRunner.withBeans(NoTracingFetcher::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
            schemaProvider.schema()
            assertThat(schemaProvider.isFieldInstrumentationEnabled("Query.hello")).isFalse
        }
    }

    @Test
    fun disableInstrumentationForAsyncDataFetchers() {
        @DgsComponent
        class NoTracingDataFetcher {
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(): CompletableFuture<String> {
                return CompletableFuture.supplyAsync { "hello" }
            }
        }

        contextRunner.withBeans(NoTracingDataFetcher::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
            schemaProvider.schema()
            assertThat(schemaProvider.isFieldInstrumentationEnabled("Query.hello")).isFalse
        }
    }

    @Test
    fun enableInstrumentationForAsyncDataFetchersWithAnnotation() {
        @DgsComponent
        class NoTracingDataFetcher {
            @DgsEnableDataFetcherInstrumentation(true)
            @DgsData(parentType = "Query", field = "hello")
            fun someFetcher(): CompletableFuture<String> {
                return CompletableFuture.supplyAsync { "hello" }
            }
        }

        contextRunner.withBeans(NoTracingDataFetcher::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
            schemaProvider.schema()
            assertThat(schemaProvider.isFieldInstrumentationEnabled("Query.hello")).isTrue
        }
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

        @DgsComponent
        class TitleFetcher {
            @DgsData(parentType = "Video", field = "title")
            fun someFetcher(): String {
                return "Title on Interface"
            }
        }

        contextRunner.withBeans(TitleFetcher::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
            schemaProvider.schema(schema)
            assertThat(schemaProvider.isFieldInstrumentationEnabled("Video.title")).isTrue
            assertThat(schemaProvider.isFieldInstrumentationEnabled("Show.title")).isTrue
        }
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

        @DgsComponent
        class TitleFetcher {
            @DgsEnableDataFetcherInstrumentation(false)
            @DgsData(parentType = "Video", field = "title")
            fun someFetcher(): String {
                return "Title on Interface"
            }
        }

        contextRunner.withBeans(VideoFetcher::class, TitleFetcher::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
            schemaProvider.schema(schema)
            assertThat(schemaProvider.isFieldInstrumentationEnabled("Video.title")).isFalse
            assertThat(schemaProvider.isFieldInstrumentationEnabled("Show.title")).isFalse
        }
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

        @DgsComponent
        class TitleFetcher {
            @DgsData(parentType = "Video", field = "title")
            fun someFetcher(): CompletableFuture<String> {
                return CompletableFuture.supplyAsync { "Title on Interface" }
            }
        }

        contextRunner.withBeans(VideoFetcher::class, TitleFetcher::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
            schemaProvider.schema(schema)
            assertThat(schemaProvider.isFieldInstrumentationEnabled("Video.title")).isFalse
            assertThat(schemaProvider.isFieldInstrumentationEnabled("Show.title")).isFalse
        }
    }

    @Test
    fun `DataFetcher with @DgsQuery annotation without field name`() {
        @DgsComponent
        class Fetcher {
            @DgsQuery
            fun hello(): String {
                return "Hello"
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
            val schema = schemaProvider.schema()
            val build = GraphQL.newGraphQL(schema).build()
            assertHello(build)
        }
    }

    @Test
    fun `DataFetcher with @DgsData annotation without field name`() {
        @DgsComponent
        class Fetcher {
            @DgsData(parentType = "Query")
            fun hello(): String {
                return "Hello"
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
            val schema = schemaProvider.schema()
            val build = GraphQL.newGraphQL(schema).build()
            assertHello(build)
        }
    }

    @Test
    fun `DataFetcher with @DgsQuery annotation with field name`() {
        @DgsComponent
        class Fetcher {
            @DgsQuery(field = "hello")
            fun someName(): String {
                return "Hello"
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schema = schemaProvider(applicationContext = context).schema()
            val build = GraphQL.newGraphQL(schema).build()
            assertHello(build)
        }
    }

    @Test
    fun `DataFetcher with @DgsMutation annotation without field name`() {
        @DgsComponent
        class Fetcher {
            @DgsMutation
            fun addMessage(@InputArgument message: String): String {
                return message
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schema = schemaProvider(applicationContext = context).schema(
                """
            type Mutation {
                addMessage(message: String): String
            }
                """.trimIndent()
            )
            val build = GraphQL.newGraphQL(schema).build()
            assertInputMessage(build)
        }
    }

    @Test
    fun `DataFetcher with @DgsMutation annotation with field name`() {
        @DgsComponent
        class Fetcher {
            @DgsMutation(field = "addMessage")
            fun someName(@InputArgument message: String): String {
                return message
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schema = schemaProvider(applicationContext = context).schema(
                """
            type Mutation {
                addMessage(message: String): String
            }
                """.trimIndent()
            )
            val build = GraphQL.newGraphQL(schema).build()
            assertInputMessage(build)
        }
    }

    @Test
    fun `Subscription dataFetcher with @DgsSubscription annotation without field name`() {
        @DgsComponent
        class Fetcher {
            @DgsSubscription
            fun messages(): Publisher<String> {
                return Flux.just("hello")
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schema = schemaProvider(applicationContext = context).schema(
                """
            type Subscription {
                messages: String
            }
                """.trimIndent()
            )
            val build = GraphQL.newGraphQL(schema).build()
            assertSubscription(build)
        }
    }

    @Test
    fun `Subscription dataFetcher with @DgsSubscription annotation with field name`() {
        @DgsComponent
        class Fetcher {
            @DgsSubscription(field = "messages")
            fun someMethod(): Publisher<String> {
                return Flux.just("hello")
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schema = schemaProvider(applicationContext = context).schema(
                """
            type Subscription {
                messages: String
            }
                """.trimIndent()
            )
            val build = GraphQL.newGraphQL(schema).build()
            assertSubscription(build)
        }
    }

    annotation class TestAnnotation

    @Test
    fun `SchemaProvider with component filter`() {
        @DgsComponent
        @TestAnnotation
        class Fetcher1 {
            @DgsQuery(field = "hello")
            fun someName(): String {
                return "Goodbye"
            }
        }

        @DgsComponent
        class Fetcher2 {
            @DgsQuery(field = "hello")
            fun someName(): String {
                return "Hello"
            }
        }

        contextRunner.withBeans(Fetcher1::class, Fetcher2::class).run { context ->
            val schema = schemaProvider(
                applicationContext = context,
                componentFilter = { !it::class.hasAnnotation<TestAnnotation>() }
            ).schema()
            val build = GraphQL.newGraphQL(schema).build()
            assertHello(build)
        }
    }

    @Test
    fun `@DgsData annotation not matching any field on the schema should fail`() {
        @DgsComponent
        class Fetcher {
            @DgsData(parentType = "Query")
            fun hell(): String {
                return "Hello"
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
            assertThrows<DataFetcherSchemaMismatchException> {
                schemaProvider.schema()
            }
        }
    }

    @Test
    fun `@DgsData annotation not matching a field on extension should not fail`() {
        @DgsComponent
        class Fetcher {
            @DgsData(parentType = "Query")
            fun world(): String {
                return "World"
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
            val schema = schemaProvider.schema()
            val build = GraphQL.newGraphQL(schema).build()
            val executionResult = build.execute("{world}")
            assertTrue(executionResult.isDataPresent)
            val data = executionResult.getData<Map<String, *>>()
            assertEquals("World", data["world"])
        }
    }

    @Test
    fun `@DgsData annotation not matching any field on the schema should fail - interface`() {
        @DgsComponent
        class Fetcher {
            @DgsData(parentType = "Character")
            fun nam(): String {
                return "Hello"
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
            assertThrows<DataFetcherSchemaMismatchException> {
                schemaProvider.schema()
            }
        }
    }

    @Test
    fun `@DgsData annotation not matching a field on extension should not fail - interface`() {
        @DgsComponent
        class Fetcher {
            @DgsData(parentType = "Character")
            fun age(): Int {
                return 42
            }

            @DgsData(parentType = "Query")
            fun character(): Map<String, Any> {
                return mapOf()
            }
        }

        @DgsComponent
        class FetcherWithDefaultResolver {
            @DgsTypeResolver(name = "Character")
            @DgsDefaultTypeResolver
            fun resolveType(@Suppress("unused_parameter") type: Any): String {
                return "Human"
            }
        }

        contextRunner.withBeans(Fetcher::class, FetcherWithDefaultResolver::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
            val schema = schemaProvider.schema()
            val build = GraphQL.newGraphQL(schema).build()
            val executionResult = build.execute("{character { age }}")
            assertTrue(executionResult.isDataPresent)
            val data = executionResult.getData<Map<String, *>>()
            assertNotNull(data["character"])
            assertEquals(42, (data["character"] as Map<*, *>)["age"])
        }
    }

    @Test
    fun `@DgsQuery annotation not matching any field on the schema should fail`() {
        @DgsComponent
        class Fetcher {
            @DgsQuery
            fun hell(): String {
                return "Hello"
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
            val ex = assertThrows<DataFetcherSchemaMismatchException> {
                schemaProvider.schema()
            }
            assertThat(ex).message().contains("a")
        }
    }

    @Test
    fun `@DgsQuery annotation not matching a field on extension should not fail`() {
        @DgsComponent
        class Fetcher {
            @DgsQuery
            fun world(): String {
                return "World"
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schemaProvider = schemaProvider(applicationContext = context)
            val schema = schemaProvider.schema()
            val build = GraphQL.newGraphQL(schema).build()
            val executionResult = build.execute("{world}")
            assertTrue(executionResult.isDataPresent)
            val data = executionResult.getData<Map<String, *>>()
            assertEquals("World", data["world"])
        }
    }

    @Test
    fun `When schemaWiringValidationEnabled is disabled declaring a data fetcher with no matching field should not fail`() {
        @DgsComponent
        class Fetcher {
            @DgsQuery
            fun hell(): String {
                return "Hello"
            }
        }

        contextRunner.withBeans(Fetcher::class).run { context ->
            val schemaWiringValidationEnabled = false
            val schemaProvider = schemaProvider(
                applicationContext = context,
                schemaWiringValidationEnabled = schemaWiringValidationEnabled
            )
            assertDoesNotThrow { schemaProvider.schema() }
        }
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

    private fun ApplicationContextRunner.withBeans(vararg beanClasses: KClass<*>): ApplicationContextRunner {
        var context = this
        for (klazz in beanClasses) {
            context = context.withBean(klazz.java)
        }
        return context
    }
}
