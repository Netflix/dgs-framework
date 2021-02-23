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
import graphql.language.*
import graphql.schema.GraphQLType
import graphql.schema.idl.TypeDefinitionRegistry
import org.springframework.data.repository.core.RepositoryMetadata
import org.springframework.data.repository.support.Repositories
import java.lang.reflect.Method
import java.util.*
import javax.annotation.PostConstruct

@DgsComponent
class RepositoryDatafetcherManager(val repositories: Repositories) {

    private val typeDefinitionRegistry = TypeDefinitionRegistry()

    @PostConstruct
    fun createQueryFields() {

        val repositoryInfos =  repositories.map { repositories.getRequiredRepositoryInformation(it) }.toList()
        println(repositoryInfos)

        val queryTypeBuilder = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name("Query")
//        repositoryBeans.forEach { beanDefinitionType ->
//            beanDefinitionType.repositoryMetadata.repositoryInterface.methods.filter { m -> m.name.startsWith("find") }
//                .forEach {
//                    createQueryField(it, beanDefinitionType.repositoryMetadata, queryTypeBuilder)
//                }
//        }

        typeDefinitionRegistry.add(queryTypeBuilder.build())
    }

    @DgsTypeDefinitionRegistry
    fun repositoryTypes(): TypeDefinitionRegistry {
        return typeDefinitionRegistry
    }

    private fun createQueryField(
        method: Method,
        repositoryMetadata: RepositoryMetadata,
        queryTypeBuilder: ObjectTypeExtensionDefinition.Builder
    ) {
        val entityType = if (method.returnType == Iterable::class.java) {
            ListType(TypeName(repositoryMetadata.domainType.simpleName))
        } else {
            TypeName(repositoryMetadata.domainType.simpleName)
        }

        val fieldDefinition = FieldDefinition.newFieldDefinition()
            .name(queryNamer(method.name, repositoryMetadata.domainType.simpleName))
            .type(entityType)

        if (method.parameterCount == 1) {
            val idType = getGraphQLTypeForId(repositoryMetadata.idType)
            val (paramName, typeName) = if (method.parameters[0].type == Iterable::class.java) {
                Pair("ids", ListType(idType))
            } else {
                Pair("id", idType)
            }

            fieldDefinition.inputValueDefinition(
                    InputValueDefinition.newInputValueDefinition().name(paramName).type(typeName).build())
        }

        queryTypeBuilder.fieldDefinition(fieldDefinition.build())
    }

    private fun getGraphQLTypeForId(idType: Class<*>): Type<*> {
        return when(idType) {
            Integer::class.java -> TypeName("Int")
            Long::class.java -> TypeName("Int")
            String::class.java -> TypeName("String")
            else -> TypeName("String")
        }
    }

    /**
     * Generate nice names for repository methods that are common
     */
    private fun queryNamer(fieldName: String, entityName: String): String {
        return when (fieldName) {
            "findAll" -> "all${entityName}s"
            "findById" -> entityName.decapitalize()
            else -> entityName.decapitalize() + fieldName.substringAfter("find")
        }
    }

    @PostConstruct
    fun createSchemaTypes() : Map<String, GraphQLType> {
//        return SchemaTypeGenerator().createSchemaTypes(repositoryBeans)
        return Collections.emptyMap()
    }
}