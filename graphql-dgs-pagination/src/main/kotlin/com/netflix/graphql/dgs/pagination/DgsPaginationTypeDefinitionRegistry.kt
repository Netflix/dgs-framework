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

package com.netflix.graphql.dgs.pagination

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import graphql.introspection.Introspection
import graphql.language.*
import graphql.schema.idl.TypeDefinitionRegistry

@DgsComponent
class DgsPaginationTypeDefinitionRegistry {

    @DgsTypeDefinitionRegistry
    fun registry(schemaRegistry: TypeDefinitionRegistry): TypeDefinitionRegistry {
        val definitions = schemaRegistry.types()
        val connectionTypes = parseConnectionDirective(definitions.values.toMutableList())

        val typeDefinitionRegistry = TypeDefinitionRegistry()
        typeDefinitionRegistry.addAll(connectionTypes)
        if (! schemaRegistry.directiveDefinitions.contains("connection")) {
            val directive = DirectiveDefinition.newDirectiveDefinition().name("connection")
                .directiveLocation(DirectiveLocation.newDirectiveLocation().name(Introspection.DirectiveLocation.OBJECT.name).build()).build()
            typeDefinitionRegistry.add(directive)
        }

        return typeDefinitionRegistry
    }

    private fun parseConnectionDirective(types: MutableList<TypeDefinition<*>>): List<TypeDefinition<*>> {
        val definitions = mutableListOf<ObjectTypeDefinition>()
        types.filter { it is ObjectTypeDefinition || it is InterfaceTypeDefinition || it is UnionTypeDefinition }
            .filter { it.hasDirective("connection") }
            .forEach {
                definitions.add(createConnection(it.name))
                definitions.add(createEdge(it.name))
            }

        if (types.any { it.hasDirective("connection") } && ! types.any { it.name == "PageInfo" }) {
            definitions.add(createPageInfo())
        }

        return definitions
    }

    private fun createConnection(type: String): ObjectTypeDefinition {
        return ObjectTypeDefinition.newObjectTypeDefinition()
            .name(type + "Connection")
            .fieldDefinition(FieldDefinition("edges", ListType(TypeName(type + "Edge"))))
            .fieldDefinition(FieldDefinition("pageInfo", TypeName("PageInfo")))
            .build()
    }

    private fun createEdge(type: String): ObjectTypeDefinition {
        return ObjectTypeDefinition.newObjectTypeDefinition()
            .name(type + "Edge")
            .fieldDefinition(FieldDefinition("cursor", TypeName("String")))
            .fieldDefinition(FieldDefinition("node", TypeName(type)))
            .build()
    }

    private fun createPageInfo(): ObjectTypeDefinition {
        return ObjectTypeDefinition.newObjectTypeDefinition()
            .name("PageInfo")
            .fieldDefinition(FieldDefinition("hasPreviousPage", NonNullType(TypeName("Boolean"))))
            .fieldDefinition(FieldDefinition("hasNextPage", NonNullType(TypeName("Boolean"))))
            .fieldDefinition(FieldDefinition("startCursor", TypeName("String")))
            .fieldDefinition(FieldDefinition("endCursor", TypeName("String")))
            .build()
    }
}
