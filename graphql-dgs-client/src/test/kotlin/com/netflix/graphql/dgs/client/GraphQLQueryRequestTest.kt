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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
}

class TestGraphQLQuery : GraphQLQuery() {
    override fun getOperationName(): String {
        return "test"
    }
}

class TestGraphQLMutation : GraphQLQuery("mutation") {
    override fun getOperationName(): String {
        return "testMutation"
    }
}

data class Movie(private val movieId: Int, private val name: String) {
    override fun toString(): String {
        return "{movieId:$movieId, name:\"$name\" }"
    }
}

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
