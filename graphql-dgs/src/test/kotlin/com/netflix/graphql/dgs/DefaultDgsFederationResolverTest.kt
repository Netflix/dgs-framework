/*
 * Copyright 2022 Netflix, Inc.
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

import com.apollographql.federation.graphqljava._Entity
import com.netflix.graphql.dgs.exceptions.DefaultDataFetcherExceptionHandler
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException
import com.netflix.graphql.dgs.federation.DefaultDgsFederationResolver
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.EntityFetcherRegistry
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import com.netflix.graphql.types.errors.ErrorDetail
import com.netflix.graphql.types.errors.TypedGraphQLError
import graphql.GraphQLContext
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.DataFetcherResult
import graphql.execution.ExecutionStepInfo
import graphql.execution.MergedField
import graphql.execution.ResultPath
import graphql.execution.TypeResolutionParameters
import graphql.language.Field
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingEnvironmentImpl
import graphql.schema.GraphQLList
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLUnionType
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

@Suppress("UNCHECKED_CAST")
@ExtendWith(MockKExtension::class)
class DefaultDgsFederationResolverTest {

    @MockK
    lateinit var applicationContextMock: ApplicationContext

    lateinit var dgsSchemaProvider: DgsSchemaProvider

    private val entityFetcherRegistry: EntityFetcherRegistry = EntityFetcherRegistry()

    private val dgsExceptionHandler: DataFetcherExceptionHandler = DefaultDataFetcherExceptionHandler()

    @BeforeEach
    fun setup() {
        dgsSchemaProvider = DgsSchemaProvider(
            applicationContext = applicationContextMock,
            federationResolver = Optional.empty(),
            existingTypeDefinitionRegistry = Optional.empty(),
            dataFetcherExceptionHandler = Optional.of(dgsExceptionHandler),
            entityFetcherRegistry = entityFetcherRegistry,
            methodDataFetcherFactory = MethodDataFetcherFactory(listOf()),
            enableEntityFetcherCustomScalarParsing = true
        )
    }

    @Nested
    inner class TypeResolverTests {

        @Test
        fun `Given the GraphQL Schema defines a type, it can find it via a matching class name`() {
            val schema = """
            type Query {
                something: String #Empty queries are not allowed
            }
            
            type Movie {
               movieID: ID
               title: String
            }
            """.trimIndent()

            val graphQLSchema: GraphQLSchema = buildGraphQLSchema(schema)

            val type =
                DefaultDgsFederationResolver(entityFetcherRegistry, Optional.of(dgsExceptionHandler))
                    .typeResolver()
                    .getType(
                        TypeResolutionParameters
                            .newParameters()
                            .schema(graphQLSchema)
                            .value(Movie("123", "Stranger Things"))
                            .build()
                    )
            assertThat(type.name).isEqualTo("Movie")
        }

        @Test
        fun `If the GraphQL Schema is missing the type, it will return null`() {
            val schema = """
            type Query {
                something: String
            }
            """.trimIndent()

            val graphQLSchema: GraphQLSchema = buildGraphQLSchema(schema)

            val type = DefaultDgsFederationResolver(entityFetcherRegistry, Optional.of(dgsExceptionHandler))
                .typeResolver()
                .getType(
                    TypeResolutionParameters
                        .newParameters()
                        .schema(graphQLSchema)
                        .value(Movie("123", "Stranger Things"))
                        .build()
                )
            assertThat(type).isNull()
        }

        @Test
        fun `If the GraphQL Schema defines a type, it can find it via a class to name mapping`() {
            val schema = """
            type Query {
                something: String #Empty queries are not allowed
            }
            
            #Represented by Java type Movie, but with a different name
            type DgsMovie {
               movieID: ID
               title: String
            }
            """.trimIndent()

            val graphQLSchema: GraphQLSchema = buildGraphQLSchema(schema)
            val customTypeResolver =
                object : DefaultDgsFederationResolver(entityFetcherRegistry, Optional.of(dgsExceptionHandler)) {
                    override fun typeMapping(): Map<Class<*>, String> {
                        return mapOf(Movie::class.java to "DgsMovie")
                    }
                }

            val type = customTypeResolver.typeResolver().getType(
                TypeResolutionParameters
                    .newParameters()
                    .schema(graphQLSchema)
                    .value(Movie("123", "Stranger Things"))
                    .build()
            )
            assertThat(type.name).isEqualTo("DgsMovie")
        }

        @Test
        fun `Will throw a MissingDgsEntityFetcherException, if unable to find the DGSEntityFetcher for the given __typename`() {
            val arguments = mapOf<String, Any>(_Entity.argumentName to listOf(mapOf("__typename" to "Movie")))
            val dataFetchingEnvironment = constructDFE(arguments)
            val result =
                (
                    DefaultDgsFederationResolver(entityFetcherRegistry, Optional.of(dgsExceptionHandler))
                        .entitiesFetcher()
                        .get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>
                    )

            assertThat(result).isNotNull
            assertThat(result.get().data.size).isEqualTo(1)
            assertThat(result.get().errors.size).isEqualTo(1)
            assertThat(result.get().errors[0].message).contains("MissingDgsEntityFetcherException")
        }

        @Test
        fun `Will throw a MissingFederatedQueryArgument, if they query is missing the __typename`() {
            val arguments = mapOf<String, Any>(_Entity.argumentName to listOf(mapOf("something" to "Else")))
            val dataFetchingEnvironment = constructDFE(arguments)
            val result =
                (
                    DefaultDgsFederationResolver(entityFetcherRegistry, Optional.of(dgsExceptionHandler))
                        .entitiesFetcher()
                        .get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>
                    )

            assertThat(result).isNotNull
            assertThat(result.get().data.size).isEqualTo(1)
            assertThat(result.get().errors).hasSize(1)
                .first().extracting { it.message }
                .satisfies(
                    Consumer {
                        assertThat(it)
                            .endsWith("The federated query is missing field(s) __typename")
                    }
                )
        }
    }

    @Nested
    inner class EntityFetcherSuccessfulInteractionsTests {

        @Test
        fun `Call an Entity Fetcher`() {
            val movieEntityFetcher = object {
                @DgsEntityFetcher(name = "Movie")
                fun movieEntityFetcher(values: Map<String, Any>): Movie {
                    return Movie(values["movieId"].toString())
                }
            }

            testEntityFetcher(movieEntityFetcher)
        }

        @Test
        fun `Call an Entity Fetcher with a DataFetchingEnvironment`() {
            val movieEntityFetcher = object {
                @DgsEntityFetcher(name = "Movie")
                fun movieEntityFetcher(values: Map<String, Any>, dfe: DataFetchingEnvironment?): Movie {
                    if (dfe == null) {
                        throw RuntimeException()
                    }
                    return Movie(values["movieId"].toString())
                }
            }

            testEntityFetcher(movieEntityFetcher)
        }

        @Test
        fun `Custom scalars are properly coerced in entity representations`() {
            val movieEntityFetcher = object {
                @DgsEntityFetcher(name = "Movie")
                fun movieEntityFetcher(values: Map<String, Any>): Movie {
                    assertThat(values["createdAt"]).isInstanceOf(LocalDateTime::class.java)
                    return Movie(createdAt = values["createdAt"] as? LocalDateTime)
                }
            }

            every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns mapOf(
                Pair(
                    "localDateTimeScalar",
                    LocalDateTimeScalar()
                )
            )
            every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("MovieEntityFetcher" to movieEntityFetcher)

            dgsSchemaProvider.schema(
                """
                type Query {}
                
                interface Movie @key(fields: "createdAt"){
                  createdAt: DateTime
                }
                
                scalar DateTime
                """.trimIndent()
            )

            val arguments = mapOf<String, Any>(
                _Entity.argumentName to listOf(mapOf("__typename" to "Movie", "createdAt" to "2020-01-01T11:22:33"))
            )
            val dataFetchingEnvironment = constructDFE(arguments)

            val result =
                (
                    DefaultDgsFederationResolver(entityFetcherRegistry, Optional.of(dgsExceptionHandler))
                        .entitiesFetcher()
                        .get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>
                    )

            assertThat(result).isNotNull
            assertThat(result.get().data).hasSize(1).first().isInstanceOf(Movie::class.java)
            assertThat(result.get().data.first() as Movie).extracting { it.createdAt.toString() }.isEqualTo("2020-01-01T11:22:33")
        }

        @Test
        fun `Custom scalars are properly coerced in nested entity representations`() {
            val movieEntityFetcher = object {
                @DgsEntityFetcher(name = "Movie")
                fun movieEntityFetcher(values: Map<String, Any>): Movie {
                    val inner = values["inner"] as Map<String, Any>
                    assertThat(inner["createdAt"]).isInstanceOf(LocalDateTime::class.java)
                    assertThat(inner["id"]).isEqualTo("123abc")
                    return Movie(createdAt = inner["createdAt"] as? LocalDateTime)
                }
            }

            every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns mapOf(
                Pair(
                    "localDateTimeScalar",
                    LocalDateTimeScalar()
                )
            )
            every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("MovieEntityFetcher" to movieEntityFetcher)

            dgsSchemaProvider.schema(
                """
                type Query {}
                
                type Movie @key(fields: "inner { id createdAt }"){
                  inner: InnerType
                }
                
                type InnerType {
                  id: ID
                  createdAt: DateTime
                }
                
                scalar DateTime
                """.trimIndent()
            )

            val arguments = mapOf<String, Any>(
                _Entity.argumentName to listOf(mapOf("__typename" to "Movie", "inner" to mapOf("id" to "123abc", "createdAt" to "2020-01-01T11:22:33")))
            )
            val dataFetchingEnvironment = constructDFE(arguments)

            val result =
                (
                    DefaultDgsFederationResolver(entityFetcherRegistry, Optional.of(dgsExceptionHandler))
                        .entitiesFetcher()
                        .get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>
                    )

            assertThat(result).isNotNull
            assertThat(result.get().data).hasSize(1).first().isInstanceOf(Movie::class.java)
            assertThat(result.get().data.first() as Movie).extracting { it.createdAt.toString() }.isEqualTo("2020-01-01T11:22:33")
        }

        @Test
        fun `Call an Entity Fetcher with a DgsDataFetchingEnvironment`() {
            val movieEntityFetcher = object {
                @DgsEntityFetcher(name = "Movie")
                fun movieEntityFetcher(values: Map<String, Any>, dfe: DgsDataFetchingEnvironment?): Movie {
                    if (dfe == null) {
                        throw RuntimeException()
                    }
                    return Movie(values["movieId"].toString())
                }
            }

            testEntityFetcher(movieEntityFetcher)
        }

        @Nested
        inner class EntityFetcherAsyncTests {
            @Test
            fun `Call an Entity Fetcher with CompletableFuture`() {
                val movieEntityFetcher = object {
                    @DgsEntityFetcher(name = "Movie")
                    fun movieEntityFetcher(values: Map<String, Any>): CompletableFuture<Movie> {
                        return CompletableFuture.completedFuture(Movie(values["movieId"].toString()))
                    }
                }

                testEntityFetcher(movieEntityFetcher)
            }

            @Test
            fun `Call an Entity Fetcher with Mono`() {
                val movieEntityFetcher = object {
                    @DgsEntityFetcher(name = "Movie")
                    fun movieEntityFetcher(values: Map<String, Any>): Mono<Movie> {
                        return Mono.just(Movie(values["movieId"].toString()))
                    }
                }

                testEntityFetcher(movieEntityFetcher)
            }
        }

        private fun testEntityFetcher(movieEntityFetcher: Any) {
            every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("MovieEntityFetcher" to movieEntityFetcher)

            dgsSchemaProvider.schema("""type Query {}""")

            val arguments = mapOf<String, Any>(
                _Entity.argumentName to listOf(mapOf("__typename" to "Movie", "movieId" to "1"))
            )
            val dataFetchingEnvironment = constructDFE(arguments)

            val result =
                (
                    DefaultDgsFederationResolver(entityFetcherRegistry, Optional.of(dgsExceptionHandler))
                        .entitiesFetcher()
                        .get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>
                    )

            assertThat(result).isNotNull
            assertThat(result.get().data).hasSize(1).first().isInstanceOf(Movie::class.java)
            assertThat(result.get().data.first() as Movie).extracting { it.movieId }.isEqualTo("1")
        }

        private fun testEntityFetcherWithoutExceptionHandler(movieEntityFetcher: Any) {
            every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("MovieEntityFetcher" to movieEntityFetcher)

            dgsSchemaProvider.schema("""type Query {}""")

            val arguments = mapOf<String, Any>(
                _Entity.argumentName to listOf(mapOf("__typename" to "Movie", "movieId" to "1"), mapOf("__typename" to "Movie", "movieId" to "2"))
            )
            val dataFetchingEnvironment = constructDFE(arguments)

            val result =
                (
                    DefaultDgsFederationResolver(entityFetcherRegistry, Optional.of(dgsExceptionHandler))
                        .entitiesFetcher()
                        .get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>
                    )

            assertThat(result).isNotNull
            assertThat(result.get().data).hasSize(1).first().isInstanceOf(Movie::class.java)
            assertThat(result.get().data.first() as Movie).extracting { it.movieId }.isEqualTo("1")
        }
    }

    @Nested
    inner class EntityFetcherErrorsTest {

        @Test
        fun `Entity fetcher returning null`() {
            val movieEntityFetcher = object {
                @DgsEntityFetcher(name = "Movie")
                fun movieEntityFetcher(@Suppress("unused_parameter") values: Map<String, Any>): Movie? {
                    return null
                }
            }

            every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("MovieEntityFetcher" to movieEntityFetcher)

            dgsSchemaProvider.schema("""type Query {}""")

            val arguments = mapOf<String, Any>(
                _Entity.argumentName to
                    listOf(mapOf("__typename" to "Movie", "movieId" to "1"))
            )
            val dataFetchingEnvironment = constructDFE(arguments)

            val result =
                (
                    DefaultDgsFederationResolver(entityFetcherRegistry, Optional.of(dgsExceptionHandler)).entitiesFetcher()
                        .get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>
                    )

            assertThat(result).isNotNull
            assertThat(result.get().data).hasSize(1)
        }

        @Test
        fun `Entity Fetcher throwing an exception`() {
            val movieEntityFetcher = object {
                @DgsEntityFetcher(name = "Movie")
                fun movieEntityFetcher(@Suppress("unused_parameter") values: Map<String, Any>): Movie {
                    throw DgsInvalidInputArgumentException("Invalid input argument exception")
                }
            }
            testExceptionEntityFetcher(movieEntityFetcher)
        }

        @Test
        fun `Entity Fetcher with failed CompletableFuture`() {
            val movieEntityFetcher = object {
                @DgsEntityFetcher(name = "Movie")
                fun movieEntityFetcher(values: Map<String, Any>): CompletableFuture<Movie> {
                    return CompletableFuture.supplyAsync {
                        if (values["movieId"] == "invalid") {
                            throw DgsInvalidInputArgumentException("Invalid input argument exception")
                        }

                        Movie(values["movieId"].toString())
                    }
                }
            }
            testExceptionEntityFetcher(movieEntityFetcher)
        }

        @Test
        fun `Entity Fetcher with failed Mono`() {
            val movieEntityFetcher = object {
                @DgsEntityFetcher(name = "Movie")
                fun movieEntityFetcher(values: Map<String, Any>): Mono<Movie> {
                    return Mono.fromCallable {
                        if (values["movieId"] == "invalid") {
                            throw DgsInvalidInputArgumentException("Invalid input argument exception")
                        }

                        Movie(values["movieId"].toString())
                    }
                }
            }
            testExceptionEntityFetcher(movieEntityFetcher)
        }

        private fun testExceptionEntityFetcher(movieEntityFetcher: Any) {
            every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("MovieEntityFetcher" to movieEntityFetcher)

            dgsSchemaProvider.schema("""type Query {}""")

            val arguments = mapOf<String, Any>(
                _Entity.argumentName to listOf(mapOf("__typename" to "Movie", "movieId" to "invalid"))
            )

            val dataFetchingEnvironment = constructDFE(arguments)

            val customExceptionHandler = object : DataFetcherExceptionHandler {
                var invocationCounter = 0
                override fun handleException(handlerParameters: DataFetcherExceptionHandlerParameters?): CompletableFuture<DataFetcherExceptionHandlerResult> {
                    invocationCounter++
                    return dgsExceptionHandler.handleException(handlerParameters)
                }
            }

            val result =
                (
                    DefaultDgsFederationResolver(entityFetcherRegistry, Optional.of(customExceptionHandler)).entitiesFetcher()
                        .get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>
                    )

            assertThat(result).isNotNull
            assertThat(result.get().data).hasSize(1)
            assertThat(result.get().errors).hasSize(1)
                .first().extracting { it.message }
                .satisfies(
                    Consumer {
                        assertThat(it)
                            .contains("Invalid input argument exception")
                    }
                )
            assertThat(customExceptionHandler.invocationCounter).isEqualTo(1)
        }

        @Test
        fun `Entity Fetcher called with wong number of arguments`() {
            val movieEntityFetcher = object {
                @DgsEntityFetcher(name = "Movie")
                @Suppress("unused_parameter")
                fun movieEntityFetcher(values: Map<String, Any>, illegalArgument: Int): Movie {
                    return Movie()
                }
            }
            every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("MovieEntityFetcher" to movieEntityFetcher)

            dgsSchemaProvider.schema("""type Query {}""")

            val arguments = mapOf<String, Any>(
                _Entity.argumentName to
                    listOf(
                        mapOf(
                            "__typename" to "Movie",
                            "movieId" to "invalid",
                            "illegalArgument" to 0
                        )
                    )

            )
            val dataFetchingEnvironment = constructDFE(arguments)

            val result =
                DefaultDgsFederationResolver(entityFetcherRegistry, Optional.of(dgsExceptionHandler))
                    .entitiesFetcher().get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>

            assertThat(result).isNotNull
            assertThat(result.get().data).hasSize(1)
            assertThat(result.get().errors).hasSize(1).first().extracting { it.message }
                .satisfies(Consumer { assertThat(it).contains("IllegalArgumentException") })
        }

        @Test
        fun `Entity Fetcher throws DgsEntityNotFoundException contains path in error`() {
            val movieEntityFetcher = object {
                @DgsEntityFetcher(name = "Movie")
                fun movieEntityFetcher(values: Map<String, Any>, dfe: DgsDataFetchingEnvironment?): Movie {
                    if (dfe == null) {
                        throw RuntimeException()
                    }
                    if (values["movieId"] == "2") {
                        throw DgsEntityNotFoundException("No entity found for movieId 2")
                    }
                    return Movie(values["movieId"].toString())
                }
            }
            every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("MovieEntityFetcher" to movieEntityFetcher)

            dgsSchemaProvider.schema("""type Query {}""")

            val arguments = mapOf<String, Any>(
                _Entity.argumentName to listOf(mapOf("__typename" to "Movie", "movieId" to "1"), mapOf("__typename" to "Movie", "movieId" to "2"))
            )
            val dataFetchingEnvironment = constructDFE(arguments)

            val result =
                DefaultDgsFederationResolver(entityFetcherRegistry, Optional.of(dgsExceptionHandler))
                    .entitiesFetcher().get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>

            assertThat(result).isNotNull
            assertThat(result.get().data).hasSize(2)
            assertThat(result.get().errors).hasSize(1).first().extracting { it.path }
                .satisfies(Consumer { assertThat(it.toString()).contains("_entities, 1") })
        }

        @Test
        fun `Entity Fetcher throws DgsEntityNotFoundException for different types with custom handling`() {
            // Define a mock movie entity fetcher that throws an EntityNotFoundException for movieId 1
            val movieEntityFetcher = object {
                @DgsEntityFetcher(name = "Movie")
                fun movieEntityFetcher(values: Map<String, Any>, dfe: DgsDataFetchingEnvironment?): Movie {
                    if (values["movieId"] == "1") {
                        throw DgsEntityNotFoundException("No entity found for movieId 1")
                    }
                    return Movie(values["movieId"].toString(), "Some Movie Title")
                }
            }

            // Define a mock show entity fetcher that throws an EntityNotFoundException for showId 2
            val showEntityFetcher = object {
                @DgsEntityFetcher(name = "Show")
                fun showEntityFetcher(values: Map<String, Any>, dfe: DgsDataFetchingEnvironment?): Show {
                    if (values["showId"] == "2") {
                        throw DgsEntityNotFoundException("No entity found for showId 2")
                    }
                    return Show(values["showId"].toString(), "Some Show Title")
                }
            }

            // Mock the ApplicationContext to return the mock entity fetchers
            every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(
                "MovieEntityFetcher" to movieEntityFetcher,
                "ShowEntityFetcher" to showEntityFetcher
            )

            // Initialize the schema with a minimal query type
            dgsSchemaProvider.schema("""type Query {}""")

            // Construct arguments with _entities argument
            val arguments = mapOf<String, Any>(
                _Entity.argumentName to listOf(mapOf("__typename" to "Movie", "movieId" to "1"), mapOf("__typename" to "Show", "showId" to "2"))
            )

            val dataFetchingEnvironment = constructDFE(arguments)

            // Create a custom exception handler which uses fields in DFE available when doing custom handling.
            val customExceptionHandler = DataFetcherExceptionHandler { handlerParameters ->
                if (handlerParameters?.exception is DgsEntityNotFoundException) {
                    // Check DFE field
                    val fieldName = handlerParameters.dataFetchingEnvironment.field.name

                    val exception = handlerParameters.exception
                    val graphqlError: GraphQLError =
                        TypedGraphQLError
                            .newBuilder()
                            .errorDetail(ErrorDetail.Common.ENHANCE_YOUR_CALM)
                            .message("$fieldName Error: ${exception.message}")
                            .path(handlerParameters.path)
                            .build()
                    CompletableFuture.completedFuture(
                        DataFetcherExceptionHandlerResult.newResult()
                            .error(graphqlError)
                            .build()
                    )
                } else {
                    dgsExceptionHandler.handleException(handlerParameters)
                }
            }

            // Invoke the entitiesFetcher to get the result
            val result = DefaultDgsFederationResolver(entityFetcherRegistry, Optional.of(customExceptionHandler))
                .entitiesFetcher().get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>

            // Assertions to check the result and errors
            assertThat(result).isNotNull
            assertThat(result.get().data).hasSize(2)
            assertThat(result.get().errors).hasSize(2).satisfiesExactly(
                { error -> assertThat(error.path.toString().contains("_entities, 0")) },
                { error -> assertThat(error.path.toString().contains("_entities, 1")) }
            )
            assertThat(result.get().errors).hasSize(2).satisfiesExactly(
                { error -> assertThat(error.message.contains("No entity found for movieId 1")) },
                { error -> assertThat(error.message.contains("No entity found for movieId 2")) }
            )
            assertThat(result.get().errors[0].message).contains(dataFetchingEnvironment.getDfe().field.name)
        }

        @Test
        fun `DgsEntityNotFoundException contains path indexes when multiple entities of same type not found in query`() {
            val movieEntityId1 = "111111"
            val movieEntityId2 = "222222"
            val movieEntityId3 = "333333"

            val movieEntityFetcher = object {
                @DgsEntityFetcher(name = "Movie")
                fun movieEntityFetcher(values: Map<String, Any>, dfe: DgsDataFetchingEnvironment?): Movie {
                    if (dfe == null) {
                        throw RuntimeException()
                    }
                    if (values["movieId"] == movieEntityId1) {
                        throw DgsEntityNotFoundException("No entity found for movieId $movieEntityId1")
                    }
                    if (values["movieId"] == movieEntityId2) {
                        throw DgsEntityNotFoundException("No entity found for movieId $movieEntityId2")
                    }
                    return Movie(values["movieId"].toString())
                }
            }

            every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()
            every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf("MovieEntityFetcher" to movieEntityFetcher)

            dgsSchemaProvider.schema("""type Query {}""")

            val arguments = mapOf<String, Any>(
                _Entity.argumentName to listOf(
                    mapOf("__typename" to "Movie", "movieId" to movieEntityId1),
                    mapOf("__typename" to "Movie", "movieId" to movieEntityId2),
                    mapOf("__typename" to "Movie", "movieId" to movieEntityId3)
                )
            )
            val dataFetchingEnvironment = constructDFE(arguments)

            val result =
                DefaultDgsFederationResolver(entityFetcherRegistry, Optional.empty())
                    .entitiesFetcher().get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>

            assertThat(result).isNotNull
            assertThat(result.get().data).hasSize(3).last().isNotNull.hasFieldOrPropertyWithValue("movieId", movieEntityId3)
            assertThat(result.get().errors).hasSize(2).satisfiesExactly(
                { error -> assertThat(error.path.contains("_entities, 0")) },
                { error -> assertThat(error.path.contains("_entities, 1")) }
            )
        }

        @Test
        fun `Invoking an Entity Fetcher missing an argument`() {
            val arguments = mapOf<String, Any>(_Entity.argumentName to listOf(mapOf("__typename" to "Movie")))
            val dataFetchingEnvironment = constructDFE(arguments)

            val result =
                (
                    DefaultDgsFederationResolver(entityFetcherRegistry, Optional.of(dgsExceptionHandler))
                        .entitiesFetcher().get(dataFetchingEnvironment) as CompletableFuture<DataFetcherResult<List<*>>>
                    )

            assertThat(result).isNotNull
            assertThat(result.get().data).hasSize(1)
            assertThat(result.get().errors).hasSize(1).first().extracting { it.message }
                .satisfies(Consumer { assertThat(it).contains("MissingDgsEntityFetcherException") })
        }
    }

    private fun buildGraphQLSchema(schema: String): GraphQLSchema {
        val schemaParser = SchemaParser()
        val registry = schemaParser.parse(schema)
        val schemaGenerator = SchemaGenerator()

        return schemaGenerator.makeExecutableSchema(registry, RuntimeWiring.newRuntimeWiring().build())
    }

    private fun constructDFE(arguments: Map<String, Any>): DgsDataFetchingEnvironment {
        val executionStepInfo = ExecutionStepInfo
            .newExecutionStepInfo()
            .path(ResultPath.parse("/_entities"))
            .type(
                GraphQLList.list(
                    GraphQLUnionType
                        .newUnionType()
                        .name("Entity")
                        .possibleTypes(GraphQLObjectType.newObject().name("Movie").build())
                        .build()
                )
            )
            .build()
        return DgsDataFetchingEnvironment(
            DataFetchingEnvironmentImpl
                .newDataFetchingEnvironment()
                .graphQLContext(GraphQLContext.getDefault())
                .arguments(arguments)
                .executionStepInfo(executionStepInfo)
                .mergedField(MergedField.newMergedField(Field("Movie")).build())
                .build()
        )
    }

    data class Movie(val movieId: String = "", val title: String = "", val createdAt: LocalDateTime? = null)

    data class Show(val showId: String = "", val title: String = "")
}
