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

import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import graphql.GraphQL
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import java.util.*
import kotlin.random.Random

@ExtendWith(MockKExtension::class)
class UnionDataFetcherTest {
    @MockK
    lateinit var applicationContextMock: ApplicationContext

    data class MovieSearchResult(val title: String, val length: Int)

    data class SeriesSearchResult(val name: String, val episodes: Int)

    val searchResultTypeResolver = object : Any() {
        @DgsTypeResolver(name = "SearchResult")
        fun searchResultTypes(result: Any): String {
            return when (result) {
                is MovieSearchResult -> "MovieSearchResult"
                is SeriesSearchResult -> "SeriesSearchResult"
                else -> throw RuntimeException("Unknown search result type")
            }
        }
    }

    val queryFetcher = object : Any() {
        @DgsData(parentType = "Query", field = "search")
        fun searchFetcher(): List<Any> {
            return listOf(MovieSearchResult("Extraction", 90), SeriesSearchResult("The Witcher", 15))
        }
    }

    val imdbFetcher = object : Any() {
        @DgsData(parentType = "SearchResult", field = "imdbRating")
        fun imdbRating(): Int {
            return Random.nextInt(1, 5)
        }
    }

    @Test
    fun testDataFetcherOnUnion() {
        val provider = DgsSchemaProvider(applicationContextMock, Optional.empty(), Optional.empty(), Optional.empty())

        every { applicationContextMock.getBeansWithAnnotation(DgsComponent::class.java) } returns mapOf(Pair("queryResolver", queryFetcher), Pair("searchResultTypeResolver", searchResultTypeResolver), Pair("imdbFetcher", imdbFetcher))
        every { applicationContextMock.getBeansWithAnnotation(DgsScalar::class.java) } returns emptyMap()
        every { applicationContextMock.getBeansWithAnnotation(DgsDirective::class.java) } returns emptyMap()

        val schema = provider.schema(
            """
            type Query {
                search: [SearchResult]
            }
            
            union SearchResult = MovieSearchResult | SeriesSearchResult
            
            type MovieSearchResult {
                title: String
                length: Int
                imdbRating: Int
            }
            
            type SeriesSearchResult {
                title: String
                episodes: Int
                imdbRating: Int
            }
            """.trimIndent()
        )

        val build = GraphQL.newGraphQL(schema).build()
        val executionResult = build.execute(
            """
            query {
                search {
                    ...on MovieSearchResult {
                        title
                        length
                        imdbRating
                    }
                    ...on SeriesSearchResult {
                        title
                        episodes
                        imdbRating
                    }
                }
            }
            """.trimIndent()
        )
        Assertions.assertEquals(0, executionResult.errors.size)
        Assertions.assertTrue(executionResult.isDataPresent)
        val data = executionResult.getData<Map<String, List<Map<String, *>>>>()
        Assertions.assertNotNull(data["search"]!![0]["imdbRating"])
    }
}
