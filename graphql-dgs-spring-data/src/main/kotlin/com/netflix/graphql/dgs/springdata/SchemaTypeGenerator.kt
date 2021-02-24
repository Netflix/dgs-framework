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

import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import graphql.Scalars
import graphql.language.FieldDefinition
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
import java.time.OffsetDateTime
import javax.annotation.PostConstruct


class SchemaTypeGenerator(private val repositories: Repositories) {

    private val builtTypes = emptyMap<String, GraphQLType>().toMutableMap()
    private val typeDefinitionRegistry = TypeDefinitionRegistry()

    @PostConstruct
    fun createSchemaTypes() {
        val repositoryInfos =  repositories.map { repositories.getRequiredRepositoryInformation(it) }.toList()
        repositoryInfos.forEach { repoMetadata ->
            if (! builtTypes.containsKey(sanitizeName(repoMetadata.domainType.simpleName))) {
                val domainTypeBuilder =
                    GraphQLObjectType.newObject().name(sanitizeName(repoMetadata.domainType.simpleName))
                repoMetadata.domainType.declaredFields
                    .forEach {
                        domainTypeBuilder.field(createFieldDefinition(it))
                    }
                builtTypes.putIfAbsent(sanitizeName(repoMetadata.domainType.simpleName), domainTypeBuilder.build())
            }
        }
        // TODO: How to wire up these created object types?
        //builtTypes.forEach{ it -> typeDefinitionRegistry.add(it.value)}
    }

    @DgsTypeDefinitionRegistry
    fun schemaTypes(): TypeDefinitionRegistry {
        return typeDefinitionRegistry
    }

    private fun createSchemaType(schemaType: Class<*>) : Map<String, GraphQLType> {
        if (!builtTypes.containsKey(sanitizeName(sanitizeName(schemaType.name)))) {
            val fieldBuilder = GraphQLObjectType.newObject().name(sanitizeName(schemaType.name))
            schemaType.declaredFields
                .forEach {
                    fieldBuilder
                        .field(createFieldDefinition(it))
                }
            builtTypes.putIfAbsent(sanitizeName(schemaType.name), fieldBuilder.build())
        }
        return builtTypes
    }

    private fun createFieldDefinition(field: Field) : GraphQLFieldDefinition {

        // TODO: handle custom scalars - do we need to create the scalar type ?
        // if(field.type == OffsetDateTime::class.java) {
        //  }

        return GraphQLFieldDefinition.newFieldDefinition().name(sanitizeName(field.name)).type(getGraphQLType(field)).build()
    }

    private fun getGraphQLType(field: Field) : GraphQLOutputType {
        if (field.type.isPrimitive) {
            return when (field.type.simpleName) {
                "int" -> Scalars.GraphQLInt
                "float" -> Scalars.GraphQLFloat
                "boolean" -> Scalars.GraphQLBoolean
                else -> Scalars.GraphQLString
            }
        }

        if (field.type == List::class.java) {
            return when(val typeName = (field.genericType as ParameterizedType).actualTypeArguments[0] as Class<*>)  {
                String::class.java  -> list(Scalars.GraphQLString)
                Integer::class.java -> list(Scalars.GraphQLInt)
                Boolean::class.java -> list(Scalars.GraphQLBoolean)
                Float::class.java -> list(Scalars.GraphQLFloat)
                else -> {
                    createSchemaType(typeName)
                    list(typeRef(sanitizeName(typeName.simpleName)))
                }
            }
        }

        return when(field.type)  {
            String::class.java  -> Scalars.GraphQLString
            Integer::class.java -> Scalars.GraphQLInt
            Boolean::class.java -> Scalars.GraphQLBoolean
            Float::class.java -> Scalars.GraphQLFloat
            OffsetDateTime::class.java -> typeRef("DateTime")
            else -> {
                createSchemaType(field.type)
                typeRef(sanitizeName(field.type.simpleName))
            }
        }
    }

    private fun sanitizeName(name: String) : String {
        return name.split(".", "$").last()
    }

}