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


class SchemaTypeGenerator {
//    private val builtTypes = emptyMap<String, GraphQLType>().toMutableMap()
//
//    fun createSchemaTypes(repositoryBeans: List<GraphqlRepositoryBeanDefinitionType>) : Map<String, GraphQLType> {
//        repositoryBeans.forEach { beanDefinitionType ->
//            val entityBuilder = GraphQLObjectType.newObject().name(sanitizeName(beanDefinitionType.repositoryMetadata.domainType.simpleName))
//            beanDefinitionType.repositoryMetadata.domainType.declaredFields
//                .forEach {
//                    entityBuilder
//                        .field(
//                            GraphQLFieldDefinition.newFieldDefinition().name(sanitizeName(it.name)).type(getScalarType(it.type))
//                                .build()
//                        )
//                }
//            builtTypes.putIfAbsent(sanitizeName(beanDefinitionType.repositoryMetadata.domainType.simpleName), entityBuilder.build())
//        }
//        return builtTypes
//    }
//
//    private fun createSchemaType(schemaType: Class<*>) : Map<String, GraphQLType> {
//        val fieldBuilder = GraphQLObjectType.newObject().name(sanitizeName(schemaType.name))
//        schemaType.declaredFields
//            .forEach {
//                fieldBuilder
//                    .field(
//                        GraphQLFieldDefinition.newFieldDefinition().name(sanitizeName(it.name)).type(getScalarType(it.type))
//                            .build()
//                    )
//                }
//        builtTypes.putIfAbsent(sanitizeName(schemaType.name), fieldBuilder.build())
//        return builtTypes
//    }
//
//    private fun getScalarType(typeName: Class<*>) : GraphQLOutputType {
//        // TODO handle collections, enums, unboxed types
//        return when(typeName)  {
//            String::class.java  -> Scalars.GraphQLString
//            Integer::class.java -> Scalars.GraphQLInt
//            Boolean::class.java -> Scalars.GraphQLBoolean
//            Float::class.java -> Scalars.GraphQLFloat
//            else -> {
//                createSchemaType(typeName)
//                typeRef(sanitizeName(typeName.simpleName))
//            }
//        }
//    }
//
//    private fun sanitizeName(name: String) : String {
//        return name.split(".", "$").last()
//    }

}