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

import graphql.language.ListType
import graphql.language.TypeName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.repository.CrudRepository

internal class RepositoryDatafetcherManagerTest {

    data class Person(val name: String)
    interface PersonRepository : CrudRepository<Person, Int>

    @Test
    fun `Test creating query fields`() {
        val repositoryBeans = listOf(BeanDefinitionType("personRepo", PersonRepository::class.java))

        val repositoryDatafetcherManager = RepositoryDatafetcherManager(repositoryBeans)
        repositoryDatafetcherManager.createQueryFields()

        val type = repositoryDatafetcherManager.repositoryTypes().objectTypeExtensions()["Query"]
        assertThat(type).isNotNull
        assertThat(type?.get(0)?.fieldDefinitions).isNotEmpty
        val findAll = type!![0].fieldDefinitions!!.find { it.name == "findAll" }
        assertThat(findAll?.name).isEqualTo("findAll")
        assertThat(findAll?.type.toString()).isEqualTo(ListType(TypeName("Person")).toString())
    }
}