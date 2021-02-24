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

import com.netflix.graphql.dgs.DgsCodeRegistry
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import graphql.language.*
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.idl.TypeDefinitionRegistry
import org.springframework.data.domain.Sort
import org.springframework.data.repository.core.RepositoryInformation
import org.springframework.data.repository.core.RepositoryMetadata
import org.springframework.data.repository.support.Repositories
import org.springframework.data.repository.support.RepositoryInvokerFactory
import java.lang.reflect.Method
import javax.annotation.PostConstruct


@DgsComponent
class RepositoryDatafetcherManager(private val repositories: Repositories, private val repositoryInvoker: RepositoryInvokerFactory) {

    private val typeDefinitionRegistry = TypeDefinitionRegistry()
    private val datafetchers: MutableMap<FieldCoordinates, DataFetcher<Any>> = mutableMapOf()

    @PostConstruct
    fun createQueryFields() {

        val repositoryInfos =  repositories.map { repositories.getRequiredRepositoryInformation(it) }.toList()
        val queryTypeBuilder = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name("Query")
        repositoryInfos.forEach { repoInfo ->
            if(repoInfo.crudMethods.hasFindAllMethod()) {
                val fieldDefinition =
                    createQueryField(repoInfo.crudMethods.findAllMethod.get(), repoInfo, queryTypeBuilder)
                createDataFetcher(repoInfo.crudMethods.findAllMethod.get(), repoInfo, fieldDefinition)
            }

            if(repoInfo.crudMethods.hasFindOneMethod()) {
                createQueryField(repoInfo.crudMethods.findOneMethod.get(), repoInfo, queryTypeBuilder)
            }
        }

        typeDefinitionRegistry.add(queryTypeBuilder.build())
    }

    private fun createDataFetcher(
        repositoryMethod: Method,
        repoInfo: RepositoryInformation,
        fieldDefinition: FieldDefinition
    ) {
        val repository = repositories.getRepositoryFor(repoInfo.domainType).get()
        datafetchers[FieldCoordinates.coordinates("Query", fieldDefinition.name)] = DataFetcher<Any> {
            repositoryInvoker.getInvokerFor(repoInfo.domainType).invokeFindAll(Sort.unsorted())
        }
    }

    @DgsTypeDefinitionRegistry
    fun repositoryTypes(): TypeDefinitionRegistry {
        return typeDefinitionRegistry
    }

    @DgsCodeRegistry
    fun codeRegistry(builder: GraphQLCodeRegistry.Builder, registry: TypeDefinitionRegistry): GraphQLCodeRegistry.Builder {
        datafetchers.forEach {
            builder.dataFetcher(it.key, it.value)
        }

        return builder
    }

    private fun createQueryField(
        method: Method,
        repositoryMetadata: RepositoryMetadata,
        queryTypeBuilder: ObjectTypeExtensionDefinition.Builder
    ): FieldDefinition {
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

        val build = fieldDefinition.build()
        queryTypeBuilder.fieldDefinition(build)

        return build
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

}