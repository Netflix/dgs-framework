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

package com.netflix.graphql.dgs.client.codegen

import com.netflix.graphql.client.codegen.exampleprojection.EntitiesProjectionRoot
import com.netflix.graphql.client.codegen.exampleprojection.ShowsProjectionRoot
import graphql.scalars.ExtendedScalars
import graphql.schema.Coercing
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

internal class ProjectionSerializerTest {

    @Test
    fun `Projection with argument that requires a scalar`() {
        val scalars: Map<Class<*>, Coercing<*, *>> =
            mapOf(OffsetDateTime::class.java to ExtendedScalars.DateTime.coercing)
        val projectionSerializer = ProjectionSerializer(InputValueSerializer(scalars))

        val projection = ShowsProjectionRoot()
            .reviews(3, OffsetDateTime.of(2021, 6, 16, 15, 20, 0, 0, ZoneOffset.UTC)).starScore().root
        val serialize = projectionSerializer.serialize(projection)
        assertThat(serialize).isEqualTo("{ reviews(minScore: 3, since: \"2021-06-16T15:20:00Z\")   { starScore } }")
    }

    @Test
    fun `Projection for entity with explicit schema type`() {
        // given
        val root = EntitiesProjectionRoot()
        root.onMovie(Optional.of("Movie"))
            .moveId().title().releaseYear()
            .reviews(username = "Foo", score = 10).username().score()
        // when
        val serialize = ProjectionSerializer(InputValueSerializer()).serialize(root, isFragment = true)
        // then
        assertThat(serialize).isEqualTo("""... on Entities { ... on Movie { __typename moveId title releaseYear reviews(username: "Foo", score: 10)   { username score } } }""")
    }

    @Test
    fun `Projection for entity with no explicit schema type`() {
        // given
        val root = EntitiesProjectionRoot()
        root.onMovie(Optional.empty())
            .moveId().title().releaseYear()
            .reviews(username = "Foo", score = 10).username().score()
        // when
        val serialize = ProjectionSerializer(InputValueSerializer()).serialize(root, isFragment = true)
        // then
        assertThat(serialize).isEqualTo("""... on Entities { ... on EntitiesMovieKey { __typename moveId title releaseYear reviews(username: "Foo", score: 10)   { username score } } }""")
    }
}
