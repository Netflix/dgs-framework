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
import graphql.Scalars
import graphql.language.FieldDefinition
import graphql.language.ListType
import graphql.language.ObjectTypeDefinition
import graphql.language.ObjectTypeExtensionDefinition
import graphql.language.TypeName
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList.list
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeReference.typeRef
import graphql.schema.idl.TypeDefinitionRegistry
import org.springframework.data.repository.core.RepositoryInformation
import org.springframework.data.repository.support.Repositories
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.time.OffsetDateTime
import javax.annotation.PostConstruct


@DgsComponent
class SchemaTypeGenerator(private val repositories: Repositories) {

    private val builtTypes = emptyMap<String, ObjectTypeDefinition>().toMutableMap()
    private val typeDefinitionRegistry = TypeDefinitionRegistry()

    @DgsTypeDefinitionRegistry
    fun schemaTypes(): TypeDefinitionRegistry {
        return typeDefinitionRegistry
    }

    @PostConstruct
    fun createSchemaTypes() {
        val repositoryInfos =  repositories.map { repositories.getRequiredRepositoryInformation(it) }.toList()
        repositoryInfos.forEach { repoMetadata ->
            if (! builtTypes.containsKey(sanitizeName(repoMetadata.domainType.simpleName))) {
                val domainTypeBuilder =
                    ObjectTypeDefinition.newObjectTypeDefinition().name(sanitizeName(repoMetadata.domainType.simpleName))
                repoMetadata.domainType.declaredFields
                    .forEach {
                        domainTypeBuilder.fieldDefinition(createFieldDefinition(it))
                    }
                builtTypes.putIfAbsent(sanitizeName(repoMetadata.domainType.simpleName), domainTypeBuilder.build())
            }
        }
        builtTypes.forEach {
            typeDefinitionRegistry.add(it.value)
        }
    }


    private fun createSchemaType(schemaType: Class<*>)  {
        if (!builtTypes.containsKey(sanitizeName(sanitizeName(schemaType.name)))) {
            val fieldBuilder = ObjectTypeDefinition.newObjectTypeDefinition().name(sanitizeName(schemaType.name))
            schemaType.declaredFields
                .forEach {
                    fieldBuilder
                        .fieldDefinition(createFieldDefinition(it))
                }
            builtTypes.putIfAbsent(sanitizeName(schemaType.name), fieldBuilder.build())
        }
    }

    private fun createFieldDefinition(field: Field) : FieldDefinition {

        return FieldDefinition.newFieldDefinition().name(sanitizeName(field.name)).type(getGraphQLType(field)).build()
    }

    private fun getGraphQLType(field: Field) : graphql.language.Type<*> {
        if (field.type.isPrimitive) {
            return when (field.type.simpleName) {
                "int" -> TypeName("Int")
                "float" -> TypeName("Float")
                "boolean" -> TypeName("Boolean")
                else -> TypeName("String")
            }
        }

        if (field.type == List::class.java) {
            return when(val typeName = (field.genericType as ParameterizedType).actualTypeArguments[0] as Class<*>)  {
                String::class.java  -> ListType(TypeName("String"))
                Integer::class.java -> ListType(TypeName("Int"))
                Boolean::class.java -> ListType(TypeName("Boolean"))
                Float::class.java -> ListType(TypeName("Float"))
                else -> {
                    createSchemaType(typeName)
                    ListType(TypeName(sanitizeName(typeName.simpleName)))
                }
            }
        }

        return when(field.type)  {
            String::class.java  -> TypeName("String")
            Integer::class.java -> TypeName("Int")
            Boolean::class.java -> TypeName("Boolean")
            Float::class.java -> TypeName("Float")
            OffsetDateTime::class.java -> TypeName("DateTime")
            else -> {
                createSchemaType(field.type)
                TypeName(sanitizeName(field.type.simpleName))
            }
        }
    }

    private fun sanitizeName(name: String) : String {
        return name.split(".", "$").last()
    }

}