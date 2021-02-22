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

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import graphql.language.FieldDefinition
import graphql.language.ListType
import graphql.language.ObjectTypeExtensionDefinition
import graphql.language.TypeName
import graphql.schema.idl.TypeDefinitionRegistry
import java.lang.reflect.Method
import javax.annotation.PostConstruct

@DgsComponent
class RepositoryDatafetcherManager(private val repositoryBeans: List<BeanDefinitionType>) {
    private val typeDefinitionRegistry = TypeDefinitionRegistry()

    @PostConstruct
    fun createQueryFields() {
        val queryTypeBuilder = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name("Query")

        repositoryBeans.forEach { beanDefinitionType ->
            beanDefinitionType.beanClass.methods.filter { m -> m.name.startsWith("find") }
                .forEach {
                    createQueryField(it, queryTypeBuilder)
                }
        }

        typeDefinitionRegistry.add(queryTypeBuilder.build())
    }

    @DgsTypeDefinitionRegistry
    fun repositoryTypes(): TypeDefinitionRegistry {
        return typeDefinitionRegistry
    }

    private fun createQueryField(method: Method, queryTypeBuilder: ObjectTypeExtensionDefinition.Builder) {
        val entityType = if(method.returnType == Iterable::class.java) {
            ListType(TypeName("test"))
        } else {
            TypeName(method.returnType.name)
        }

        val fieldDefinition = FieldDefinition.newFieldDefinition()
                .name(method.name)
                .type(entityType).build()

        queryTypeBuilder.fieldDefinition(fieldDefinition)
    }
}