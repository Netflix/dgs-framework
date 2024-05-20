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

package com.netflix.graphql.dgs.mvc

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.SchemaProviderResult
import graphql.Scalars
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class DgsRestSchemaJsonControllerTest {
    @MockK
    lateinit var dgsSchemaProvider: DgsSchemaProvider

    @Test
    fun normalFlow() {
        val objectType: GraphQLObjectType = GraphQLObjectType.newObject()
            .name("helloType")
            .field(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("hello")
                    .type(Scalars.GraphQLString)
            )
            .build()
        val schema = GraphQLSchema.newSchema()
            .clearSchemaDirectives()
            .clearAdditionalTypes()
            .clearDirectives()
            .query(objectType).build()

        every { dgsSchemaProvider.schema() } returns SchemaProviderResult(schema, runtimeWiring = RuntimeWiring.MOCKED_WIRING)

        val result = DgsRestSchemaJsonController(dgsSchemaProvider).schema()

        val jsonPath = JsonPath.parse(result, Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS))
        assertThat(jsonPath.read<String>("$.data.__schema.queryType.name")).isEqualTo("helloType")
        assertThat(jsonPath.read<String>("$.errors")).isNullOrEmpty()
    }
}
