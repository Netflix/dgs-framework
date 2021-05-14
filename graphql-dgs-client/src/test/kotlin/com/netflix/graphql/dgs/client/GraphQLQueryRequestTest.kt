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

package com.netflix.graphql.dgs.client

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest
import com.netflix.graphql.dgs.client.scalar.DateRange
import com.netflix.graphql.dgs.client.scalar.DateRangeScalar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GraphQLQueryRequestTest {
    @Test
    fun testSerializeListOfStringsAsInput() {
        val query = TestGraphQLQuery().apply {
            input["actors"] = "actorA"
            input["movies"] = listOf("movie1", "movie2")
        }
        val request = GraphQLQueryRequest(query)
        val result = request.serialize()
        assertThat(result).isEqualTo("query {test(actors: \"actorA\", movies: [\"movie1\", \"movie2\"]) }")
    }

    @Test
    fun testSerializeListOfIntegersAsInput() {
        val query = TestGraphQLQuery().apply {
            input["movies"] = listOf(1234, 5678)
        }
        val request = GraphQLQueryRequest(query)
        val result = request.serialize()
        assertThat(result).isEqualTo("query {test(movies: [1234, 5678]) }")
    }

    @Test
    fun testSerializeInputWithMultipleParameters() {
        val query = TestGraphQLQuery().apply {
            input["name"] = "noname"
            input["age"] = 30
        }
        val request = GraphQLQueryRequest(query)
        val result = request.serialize()
        assertThat(result).isEqualTo("query {test(name: \"noname\", age: 30) }")
    }

    @Test
    fun testSerializeInputClass() {
        val query = TestGraphQLQuery().apply {
            input["movie"] = Movie(1234, "testMovie")
        }
        val request = GraphQLQueryRequest(query)
        val result = request.serialize()
        assertThat(result).isEqualTo("query {test(movie: {movieId:1234, name:\"testMovie\" }) }")
    }

    @Test
    fun testSerializeInputClassWithProjection() {
        val query = TestGraphQLQuery().apply {
            input["movie"] = Movie(1234, "testMovie")
        }
        val request = GraphQLQueryRequest(query, MovieProjection().name().movieId())
        val result = request.serialize()
        assertThat(result).isEqualTo("query {test(movie: {movieId:1234, name:\"testMovie\" }){ name movieId } }")
    }

    @Test
    fun testSerializeMutation() {
        val query = TestGraphQLMutation().apply {
            input["movie"] = Movie(1234, "testMovie")
        }
        val request = GraphQLQueryRequest(query, MovieProjection().name().movieId())
        val result = request.serialize()
        assertThat(result).isEqualTo("mutation {testMutation(movie: {movieId:1234, name:\"testMovie\" }){ name movieId } }")
    }

    @Test
    fun serializeWithName() {
        val query = TestNamedGraphQLQuery().apply {
            input["movie"] = Movie(123, "greatMovie")
        }
        val request = GraphQLQueryRequest(query, MovieProjection().name().movieId())
        val result = request.serialize()
        assertThat(result).isEqualTo("query TestNamedQuery {test(movie: {movieId:123, name:\"greatMovie\" }){ name movieId } }")
    }

    @Test
    fun serializeWithScalar() {
        val query = TestNamedGraphQLQuery().apply {
            input["movie"] = Movie(123, "greatMovie")
            input["dateRange"] = DateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 5, 11))
        }
        val request =
            GraphQLQueryRequest(query, MovieProjection(), mapOf(DateRange::class.java to DateRangeScalar()))

        val result = request.serialize()
        assertThat(result).isEqualTo("query TestNamedQuery {test(movie: {movieId:123, name:\"greatMovie\" }, dateRange: \"01/01/2020-05/11/2021\") }")
    }

    @Test
    fun serializeWithNestedScalar() {
        val query = TestNamedGraphQLQuery().apply {
            input["movie"] = Movie(123, "greatMovie", DateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 5, 11)))
        }
        val request =
            GraphQLQueryRequest(query, MovieProjection(), mapOf(DateRange::class.java to DateRangeScalar()))

        val result = request.serialize()
        assertThat(result).isEqualTo("query TestNamedQuery {test(movie: {movieId:123, name:\"greatMovie\", window:\"01/01/2020-05/11/2021\" }) }")
    }

    @Test
    fun testSerializeMapAsInput() {
        val query = TestGraphQLQuery().apply {
            input["actors"] = mapOf("name" to "actorA", "movies" to listOf("movie1", "movie2"))
            input["movie"] = Movie(123, "greatMovie", DateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 5, 11)))
        }
        val request = GraphQLQueryRequest(query, MovieProjection(), mapOf(DateRange::class.java to DateRangeScalar()))
        val result = request.serialize()
        assertThat(result).isEqualTo("query {test(actors: { name: \"actorA\", movies: [\"movie1\", \"movie2\"] }, movie: {movieId:123, name:\"greatMovie\", window:\"01/01/2020-05/11/2021\" }) }")
    }
}

class TestGraphQLQuery : GraphQLQuery() {
    override fun getOperationName(): String {
        return "test"
    }
}

class TestNamedGraphQLQuery : GraphQLQuery("query", "TestNamedQuery") {
    override fun getOperationName(): String {
        return "test"
    }
}

class TestGraphQLMutation : GraphQLQuery("mutation") {
    override fun getOperationName(): String {
        return "testMutation"
    }
}

data class Movie(val movieId: Int, val name: String, val window: DateRange? = null)

class MovieProjection : BaseProjectionNode() {
    fun movieId(): MovieProjection {
        fields["movieId"] = null
        return this
    }

    fun name(): MovieProjection {
        fields["name"] = null
        return this
    }
}
