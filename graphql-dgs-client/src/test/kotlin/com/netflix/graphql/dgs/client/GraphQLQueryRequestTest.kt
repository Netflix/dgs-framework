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
import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

class DateRangeScalar : Coercing<DateRange, String> {
    private var formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    @Throws(CoercingSerializeException::class)
    override fun serialize(dataFetcherResult: Any): String {
        val range: DateRange = dataFetcherResult as DateRange
        return range.from.format(formatter) + "-" + range.to.format(formatter)
    }

    @Throws(CoercingParseValueException::class)
    override fun parseValue(input: Any): DateRange {
        val split = (input as String).split("-").toTypedArray()
        val from = LocalDate.parse(split[0], formatter)
        val to = LocalDate.parse(split[0], formatter)
        return DateRange(from, to)
    }

    @Throws(CoercingParseLiteralException::class)
    override fun parseLiteral(input: Any): DateRange {
        val split = (input as StringValue).value.split("-").toTypedArray()
        val from = LocalDate.parse(split[0], formatter)
        val to = LocalDate.parse(split[0], formatter)
        return DateRange(from, to)
    }
}

class DateRange(val from: LocalDate, val to: LocalDate)
