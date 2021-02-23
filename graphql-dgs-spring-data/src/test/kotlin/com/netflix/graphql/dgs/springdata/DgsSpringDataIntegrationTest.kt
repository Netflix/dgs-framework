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

package com.netflix.graphql.dgs.springdata

import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.springdata.exampleapp.DemoTestApplication
import com.netflix.graphql.dgs.springdata.exampleapp.data.ReviewEntityRepository
import com.netflix.graphql.dgs.springdata.exampleapp.data.ShowEntityRepository
import graphql.language.FieldDefinition
import graphql.language.ListType
import graphql.language.TypeName
import graphql.schema.GraphQLSchema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.event.annotation.BeforeTestMethod
import javax.persistence.EntityManager

@DataJpaTest
@ContextConfiguration(classes = [
    DgsSpringDataAutoconfiguration::class,
    DgsAutoConfiguration::class,
    DemoTestApplication::class
])
//@TestPropertySource(propeties = ["spring.jpa.hibernate.ddl-auto=validate"])
open class DgsSpringDataIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var showEntityRepository: ShowEntityRepository

    @Autowired
    private lateinit var reviewEntityRepository: ReviewEntityRepository

    @Autowired
    private lateinit var repositoryDatafetcherManager: RepositoryDatafetcherManager

    @Autowired lateinit var schemaProvider: DgsSchemaProvider

    @Autowired lateinit var schema: GraphQLSchema

    @Test
    fun `Application starts`() {
        assertThat(jdbcTemplate).isNotNull
        assertThat(entityManager).isNotNull
        assertThat(reviewEntityRepository).isNotNull
        assertThat(showEntityRepository).isNotNull
    }

    @Test
    fun `Should create all* queries for ShowEntity`() {
        val fieldDefinition = getFieldDefinition("allShowEntitys")

        assertThat(fieldDefinition?.name).isEqualTo("allShowEntitys")
        assertThat(fieldDefinition?.type.toString()).isEqualTo(ListType(TypeName("ShowEntity")).toString())
    }

    @Test
    fun `Should create a showEntity Query`() {
        val personQuery = getFieldDefinition("showEntity")
        assertThat(personQuery.name).isEqualTo("showEntity")
        assertThat(personQuery.type.toString()).isEqualTo(TypeName("ShowEntity").toString())
        assertThat(personQuery.inputValueDefinitions!![0].name).isEqualTo("id")
    }

    private fun getFieldDefinition(name: String): FieldDefinition {
        val type = repositoryDatafetcherManager.repositoryTypes().objectTypeExtensions()["Query"]
        assertThat(type).isNotNull
        assertThat(type?.get(0)?.fieldDefinitions).isNotEmpty
        val fieldDefinitions = type?.get(0)?.fieldDefinitions!!
        val fieldDefinition = fieldDefinitions.find { it.name == name }
        if (fieldDefinition == null) {
            Assertions.fail<String>("Missing field $name")
        }

        return fieldDefinition!!
    }

}