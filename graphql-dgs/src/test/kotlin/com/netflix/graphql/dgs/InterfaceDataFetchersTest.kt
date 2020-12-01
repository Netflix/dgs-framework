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

import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import graphql.GraphQL
import graphql.schema.DataFetchingEnvironment
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import java.util.*

@Suppress("UNUSED_PARAMETER")
@ExtendWith(MockKExtension::class)
class InterfaceDataFetchersTest {
    @MockK
    lateinit var applicationContextMock: ApplicationContext

    interface Movie {
        var title: String
    }

    class ScaryMovie: Movie {
        override var title: String = ""
        var gory = true
    }

    class ActionMovie: Movie {
        override var title: String = ""
        var nrOfExplosions = 0
    }

    @Test
    fun testDataFetchersOnInterface() {

        val movieTypeResolver = object: Any() {
            @DgsTypeResolver(name="Movie")
            fun movieTypes(movie: Movie): String {
                return when (movie) {
                    is ScaryMovie -> "ScaryMovie"
                    is ActionMovie -> "ActionMovie"
                    else -> throw RuntimeException("Unknown movie type")
                }
            }

        }

        val fetcher = object: Any() {
            @DgsData(parentType="Movie", field="director")
            fun directorFetcher(dfe: DataFetchingEnvironment):String {
                return "The Director"
            }
        }

        val queryFetcher = object: Any() {
            @DgsData(parentType="Query", field="movies")
            fun moviesFetcher(dfe: DataFetchingEnvironment):List<Movie> {
                return listOf(ScaryMovie(), ActionMovie())
            }
        }

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("helloFetcher", fetcher), Pair("movieTypeResolver", movieTypeResolver), Pair("queryResolver", queryFetcher))
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()

        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())
        val schema = provider.schema("""
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
        """.trimIndent())

        val build = GraphQL.newGraphQL(schema).build()
        val executionResult = build.execute("{movies {director}}")
         assertEquals(0, executionResult.errors.size)
        assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, List<Map<String, *>>>>()
        assertEquals("The Director", data["movies"]!![0]["director"])
        assertEquals("The Director", data["movies"]!![1]["director"])
    }
}