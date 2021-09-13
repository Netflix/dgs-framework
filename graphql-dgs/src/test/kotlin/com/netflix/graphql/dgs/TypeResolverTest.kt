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
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import graphql.GraphQL
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import java.util.*

@ExtendWith(MockKExtension::class)
class TypeResolverTest {
    @MockK
    lateinit var applicationContextMock: ApplicationContext

    interface Movie {
        var title: String
    }

    class ScaryMovie : Movie {
        override var title: String = ""
        var gory = true
    }

    class ActionMovie : Movie {
        override var title: String = ""
        var nrOfExplosions = 0
    }

    val queryFetcher = object : Any() {
        @DgsData(parentType = "Query", field = "movies")
        fun moviesFetcher(): List<Movie> {
            return listOf(ScaryMovie(), ActionMovie())
        }
    }

    @Test
    fun testFallbackTypeResolver() {

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = provider.schema(
            """
            type Query {
                movies: [Movie]            
            }
            
            interface Movie {
                title: String
                director: String
            }
            
            type ScaryMovie implements Movie {
                title: String
                director: String
                gory: Boolean
            }
            
            type ActionMovie implements Movie {
                title: String
                director: String
                nrOfExplosions: Int
            }
            """.trimIndent()
        )

        val build = GraphQL.newGraphQL(schema).build()
        val executionResult = build.execute(
            """
            {
                movies { 
                    title 
                    ...on ScaryMovie { 
                        gory
                    }
                }
            }
            """.trimIndent()
        )
        Assertions.assertEquals(0, executionResult.errors.size)
    }

    /**
     * When the Java class name and GraphQL type name are not the same, an Exception is thrown.
     */
    @Test
    fun testFallbackTypeResolverError() {

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("queryResolver", queryFetcher))
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = provider.schema(
            """
            type Query {
                movies: [Movie]            
            }
            
            interface Movie {
                title: String
                director: String
            }
            
            type ScaryMovie implements Movie {
                title: String
                director: String
                gory: Boolean
            }
            
            type ActionMovieWithSpecialName implements Movie {
                title: String
                director: String
                nrOfExplosions: Int
            }
            """.trimIndent()
        )

        val build = GraphQL.newGraphQL(schema).build()

        assertThrows<InvalidTypeResolverException> {
            build.execute(
                """
                {
                    movies { 
                        title 
                        ...on ActionMovieWithSpecialName { 
                            nrOfExplosions
                        }
                    }
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun testCustomTypeResolver() {
        val movieTypeResolver = object : Any() {
            @DgsTypeResolver(name = "Movie")
            fun movieTypes(movie: Movie): String {
                return when (movie) {
                    is ScaryMovie -> "ScaryMovie"
                    is ActionMovie -> "ActionMovie"
                    else -> throw RuntimeException("Unknown movie type")
                }
            }
        }

        val typeResolverSpy = spyk(movieTypeResolver)
        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("MovieTypeResolver", typeResolverSpy), Pair("queryResolver", queryFetcher))
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = provider.schema(
            """
            type Query {
                movies: [Movie]            
            }
            
            interface Movie {
                title: String
                director: String
            }
            
            type ScaryMovie implements Movie {
                title: String
                director: String
                gory: Boolean
            }
            
            type ActionMovie implements Movie {
                title: String
                director: String
                nrOfExplosions: Int
            }
            """.trimIndent()
        )

        val build = GraphQL.newGraphQL(schema).build()
        val executionResult = build.execute(
            """
            {
                movies { 
                    title 
                    ...on ScaryMovie { 
                        gory
                    }
                }
            }
            """.trimIndent()
        )
        Assertions.assertEquals(0, executionResult.errors.size)

        verify { typeResolverSpy.movieTypes(any()) }

        confirmVerified(typeResolverSpy)
    }
}
