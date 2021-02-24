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

import graphql.language.FieldDefinition
import graphql.language.ListType
import graphql.language.TypeName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.support.Repositories

@SpringBootTest
@SpringBootConfiguration
@EnableAutoConfiguration
internal open class RepositoryDatafetcherManagerTest {

    data class Person(val name: String)

    interface PersonRepository : CrudRepository<Person, Int>

    @Autowired
    private lateinit var repositories: Repositories

    private lateinit var repositoryDatafetcherManager: RepositoryDatafetcherManager

    @BeforeEach
    fun before() {
        repositoryDatafetcherManager = RepositoryDatafetcherManager(repositories, repositoryInvoker)
        repositoryDatafetcherManager.createQueryFields()
    }

    @Test
    fun `Should create a allPersons query`() {
        val fieldDefinition = getFieldDefinition("allShowEntitys")

        assertThat(fieldDefinition?.name).isEqualTo("allShowEntitys")
        assertThat(fieldDefinition?.type.toString()).isEqualTo(ListType(TypeName("ShowEntity")).toString())
    }

    @Test
    fun `Should create a person Query`() {
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
        if(fieldDefinition == null) {
            Assertions.fail<String>("Missing field $name")
        }

        return fieldDefinition!!

    }

}